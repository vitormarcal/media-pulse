package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowCreditsBatchSyncResponse
import dev.marcal.mediapulse.server.api.shows.ShowCreditsSyncResponse
import dev.marcal.mediapulse.server.api.shows.ShowPersonCreditDto
import dev.marcal.mediapulse.server.api.shows.ShowTmdbCreditCandidateDto
import dev.marcal.mediapulse.server.api.shows.ShowTmdbCreditCandidateGroupDto
import dev.marcal.mediapulse.server.api.shows.ShowTmdbCreditCandidatesResponse
import dev.marcal.mediapulse.server.api.shows.ShowTmdbCreditImportRequest
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.movie.MovieCreditType
import dev.marcal.mediapulse.server.model.person.Person
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.PersonCleanupRepository
import dev.marcal.mediapulse.server.repository.TvShowQueryRepository
import dev.marcal.mediapulse.server.repository.crud.PersonRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditsCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class ShowCreditsService(
    private val tvShowRepository: TvShowRepository,
    private val personRepository: PersonRepository,
    private val showCreditAssignmentRepository: ShowCreditAssignmentRepository,
    private val showCreditsCrudRepository: ShowCreditsCrudRepository,
    private val tvShowQueryRepository: TvShowQueryRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualShowCatalogService: ManualShowCatalogService,
    private val transactionTemplate: TransactionTemplate,
    private val personCleanupRepository: PersonCleanupRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val relevantCrewJobs =
        setOf(
            "Director",
            "Writer",
            "Screenplay",
            "Story Editor",
        )

    private val castLimit = 12
    private val crewLimit = 6
    private val writerJobs = setOf("Writer", "Screenplay", "Story Editor")

    @Transactional
    fun syncFromTmdb(showId: Long): ShowCreditsSyncResponse =
        syncFromTmdbInternal(showId).also { showCreditsCrudRepository.clearCurated(showId) }

    @Transactional(readOnly = true)
    fun fetchTmdbCandidates(showId: Long): ShowTmdbCreditCandidatesResponse {
        val show = requireShow(showId)
        val credits = requireTmdbCredits(show)
        val linkedKeys = tvShowQueryRepository.getShowPeople(showId).mapTo(linkedSetOf(), ::categoryKey)
        val cast = credits.cast.sortedWith(showCastComparator).distinctBy { it.tmdbId }
        val directors =
            credits.crew
                .filter { it.job == "Director" }
                .sortedWith(showCrewComparator)
                .distinctBy { it.tmdbId }
        val writers =
            credits.crew
                .filter { it.job in writerJobs }
                .sortedWith(showCrewComparator)
                .distinctBy { it.tmdbId }
        val groups =
            listOf(
                showCandidateGroup("cast", "Elenco do TMDb", cast.mapNotNull { buildCandidate(linkedKeys, it) }),
                showCandidateGroup("directors", "Direção do TMDb", directors.mapNotNull { buildCandidate(linkedKeys, it) }),
                showCandidateGroup("writers", "Roteiro do TMDb", writers.mapNotNull { buildCandidate(linkedKeys, it) }),
            ).filter { it.items.isNotEmpty() }
        return ShowTmdbCreditCandidatesResponse(show.id, groups.sumOf { it.items.size }, groups)
    }

    @Transactional
    fun importTmdbCredit(
        showId: Long,
        request: ShowTmdbCreditImportRequest,
    ): ShowPersonCreditDto {
        val show = requireShow(showId)
        val credits = requireTmdbCredits(show)
        val assignment =
            when (request.creditType.name) {
                "CAST" -> {
                    val match =
                        credits.cast.firstOrNull { it.tmdbId == request.personTmdbId }
                            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Crédito de elenco do TMDb não encontrado")
                    val person = upsertPerson(match.tmdbId, match.name, match.profilePath)
                    ShowCreditAssignmentRepository.UpsertShowCreditRequest(
                        show.id,
                        person.id,
                        MovieCreditType.CAST,
                        characterName = match.character,
                        billingOrder = match.order,
                    )
                }
                "CREW" -> {
                    val match =
                        credits.crew.firstOrNull { it.tmdbId == request.personTmdbId && it.job == request.job }
                            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Crédito de equipe do TMDb não encontrado")
                    if (match.job !in
                        relevantCrewJobs
                    ) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Crédito fora do recorte audiovisual")
                    }
                    val person = upsertPerson(match.tmdbId, match.name, match.profilePath)
                    ShowCreditAssignmentRepository.UpsertShowCreditRequest(
                        show.id,
                        person.id,
                        MovieCreditType.CREW,
                        department = match.department,
                        job = match.job,
                    )
                }
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "creditType inválido")
            }
        showCreditAssignmentRepository.upsert(assignment)
        showCreditsCrudRepository.markCurated(show.id)
        return tvShowQueryRepository.getShowPeople(show.id).first {
            it.personId == assignment.personId &&
                it.creditType.name == assignment.creditType.name &&
                (it.job ?: "") == (assignment.job ?: "")
        }
    }

    @Transactional
    fun removeCredit(
        showId: Long,
        personId: Long,
        rawCategory: String,
    ) {
        requireShow(showId)
        val category = normalizeCategory(rawCategory)
        if (showCreditAssignmentRepository.deleteCategory(showId, personId, category) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Crédito não encontrado")
        }
        showCreditsCrudRepository.markCurated(showId)
        personCleanupRepository.deleteIfOrphanAndNotFavorite(personId)
    }

    @Transactional
    fun syncFromTmdbIfLinked(showId: Long) {
        val show = requireShow(showId)
        if (show.creditsCuratedAt != null) return
        val hasTmdbLink = show.tmdbId != null

        if (hasTmdbLink) {
            syncFromTmdbInternal(showId)
        }
    }

    fun syncAllFromTmdb(limit: Int = 100): ShowCreditsBatchSyncResponse {
        val requestedLimit = limit.coerceIn(1, 1000)
        val pendingTotal = showCreditsCrudRepository.countPendingTmdbSyncCandidates()
        val candidates = showCreditsCrudRepository.findTmdbSyncCandidates(requestedLimit)
        var synced = 0
        var failed = 0
        var processed = 0

        logger.info(
            "Show credits TMDb batch sync started | requestedLimit={} | pendingTotal={} | selectedCandidates={}",
            requestedLimit,
            pendingTotal,
            candidates.size,
        )

        candidates.forEach { candidate ->
            runCatching {
                transactionTemplate.execute {
                    syncFromTmdbInternal(candidate.showId)
                } ?: error("Batch show credit sync transaction returned null")
            }.onSuccess {
                synced++
            }.onFailure { ex ->
                failed++
                runCatching {
                    transactionTemplate.execute {
                        showCreditsCrudRepository.markCreditsSyncFailure(
                            candidate.showId,
                            ex.message ?: ex.javaClass.simpleName,
                        )
                    }
                }.onFailure { persistenceError ->
                    logger.error(
                        "Failed to persist show credits TMDb sync failure | showId={} tmdbId={}",
                        candidate.showId,
                        candidate.tmdbId,
                        persistenceError,
                    )
                }
                logger.warn(
                    "Failed to sync show credits from TMDb in batch | showId={} tmdbId={}",
                    candidate.showId,
                    candidate.tmdbId,
                    ex,
                )
            }

            processed++
            logger.info(
                "Show credits TMDb batch sync progress | processed={} | synced={} | failed={} | remainingInBatch={} | remainingPendingEstimate={}",
                processed,
                synced,
                failed,
                (candidates.size - processed).coerceAtLeast(0),
                (pendingTotal - processed).coerceAtLeast(0),
            )
        }

        return ShowCreditsBatchSyncResponse(
            requestedLimit = requestedLimit,
            candidates = candidates.size,
            processed = processed,
            synced = synced,
            failed = failed,
        ).also { response ->
            logger.info(
                "Show credits TMDb batch sync finished | requestedLimit={} | pendingTotal={} | candidates={} | processed={} | synced={} | failed={}",
                requestedLimit,
                pendingTotal,
                response.candidates,
                response.processed,
                response.synced,
                response.failed,
            )
        }
    }

    private fun syncFromTmdbInternal(showId: Long): ShowCreditsSyncResponse {
        val show = requireShow(showId)
        val credits = requireTmdbCredits(show)
        val previousPersonIds = tvShowQueryRepository.getShowPeople(showId).map { it.personId }.toSet()

        val castCredits =
            credits.cast
                .sortedWith(showCastComparator)
                .distinctBy { it.tmdbId }
                .take(castLimit)
                .map { credit ->
                    val person = upsertPerson(credit.tmdbId, credit.name, credit.profilePath)
                    ShowCreditAssignmentRepository.UpsertShowCreditRequest(
                        showId = show.id,
                        personId = person.id,
                        creditType = MovieCreditType.CAST,
                        characterName = credit.character ?: "",
                        billingOrder = credit.order,
                    )
                }

        val selectedCrew =
            credits.crew
                .filter { it.job == "Director" }
                .sortedWith(showCrewComparator)
                .distinctBy { it.tmdbId }
                .take(crewLimit) +
                credits.crew
                    .filter { it.job in writerJobs }
                    .sortedWith(showCrewComparator)
                    .distinctBy { it.tmdbId }
                    .take(crewLimit)
        val crewCredits =
            selectedCrew
                .map { credit ->
                    val person = upsertPerson(credit.tmdbId, credit.name, credit.profilePath)
                    ShowCreditAssignmentRepository.UpsertShowCreditRequest(
                        showId = show.id,
                        personId = person.id,
                        creditType = MovieCreditType.CREW,
                        department = credit.department ?: "",
                        job = credit.job ?: "",
                    )
                }

        val resolvedCredits =
            (castCredits + crewCredits)
                .distinctBy { listOf(it.personId, it.creditType.name, it.job ?: "", it.characterName ?: "") }

        showCreditAssignmentRepository.replaceForShow(show.id, resolvedCredits)
        previousPersonIds.forEach(personCleanupRepository::deleteIfOrphanAndNotFavorite)

        return ShowCreditsSyncResponse(
            showId = show.id,
            syncedCount = resolvedCredits.size,
            visibleCount = tvShowQueryRepository.getShowPeople(show.id).size,
            curated = false,
        ).also {
            showCreditsCrudRepository.markCreditsSynced(show.id)
        }
    }

    private fun requireShow(showId: Long): TvShow =
        tvShowRepository.findById(showId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")
        }

    private fun requireTmdbId(showId: Long): String =
        tvShowRepository.findById(showId).orElse(null)?.tmdbId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Série sem vínculo TMDb")

    private fun requireTmdbCredits(show: TvShow): TmdbApiClient.TmdbShowCredits =
        tmdbApiClient.fetchShowCredits(show.tmdbId ?: requireTmdbId(show.id))
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb show credits not found")

    private val showCastComparator =
        compareByDescending<TmdbApiClient.TmdbShowCastCredit> { it.episodeCount }
            .thenBy { it.order ?: Int.MAX_VALUE }
            .thenBy { it.name }

    private val showCrewComparator =
        compareByDescending<TmdbApiClient.TmdbShowCrewCredit> { it.episodeCount }.thenBy { it.name }

    private fun buildCandidate(
        linkedKeys: Set<String>,
        credit: TmdbApiClient.TmdbShowCastCredit,
    ): ShowTmdbCreditCandidateDto? {
        if (categoryKey(credit.tmdbId, "CAST") in linkedKeys) return null
        return ShowTmdbCreditCandidateDto(
            credit.tmdbId,
            credit.name,
            credit.profilePath?.let(manualShowCatalogService::buildTmdbImageUrl),
            dev.marcal.mediapulse.server.api.shows.ShowCreditTypeDto.CAST,
            null,
            null,
            credit.character,
            credit.order,
            credit.character ?: "Elenco",
        )
    }

    private fun buildCandidate(
        linkedKeys: Set<String>,
        credit: TmdbApiClient.TmdbShowCrewCredit,
    ): ShowTmdbCreditCandidateDto? {
        val category = if (credit.job == "Director") "DIRECTING" else "WRITING"
        if (categoryKey(credit.tmdbId, category) in linkedKeys) return null
        return ShowTmdbCreditCandidateDto(
            credit.tmdbId,
            credit.name,
            credit.profilePath?.let(manualShowCatalogService::buildTmdbImageUrl),
            dev.marcal.mediapulse.server.api.shows.ShowCreditTypeDto.CREW,
            credit.department,
            credit.job,
            null,
            null,
            if (category == "DIRECTING") "Direção" else "Roteiro",
        )
    }

    private fun showCandidateGroup(
        id: String,
        title: String,
        items: List<ShowTmdbCreditCandidateDto>,
    ) = ShowTmdbCreditCandidateGroupDto(id, title, items)

    private fun normalizeCategory(raw: String): String =
        raw.trim().uppercase().takeIf { it in setOf("CAST", "DIRECTING", "WRITING") }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "category inválida")

    private fun categoryKey(dto: ShowPersonCreditDto): String =
        categoryKey(
            dto.tmdbId,
            when {
                dto.creditType.name == "CAST" -> "CAST"
                dto.job == "Director" -> "DIRECTING"
                else -> "WRITING"
            },
        )

    private fun categoryKey(
        tmdbId: String,
        category: String,
    ): String = "$tmdbId|$category"

    private fun upsertPerson(
        tmdbId: String,
        rawName: String,
        profilePath: String?,
    ): Person {
        val name = rawName.trim().replace("\\s+".toRegex(), " ")
        val normalizedName = name.lowercase()
        val profileUrl = profilePath?.let(manualShowCatalogService::buildTmdbImageUrl)
        val slug = SlugTextUtil.normalize("$name $tmdbId", maxLength = 80)
        val existing = personRepository.findByTmdbId(tmdbId)
        return if (existing == null) {
            personRepository.save(
                Person(
                    tmdbId = tmdbId,
                    name = name,
                    normalizedName = normalizedName,
                    slug = slug,
                    profileUrl = profileUrl,
                ),
            )
        } else {
            personRepository.save(
                existing.copy(
                    name = name,
                    normalizedName = normalizedName,
                    slug = slug,
                    profileUrl = profileUrl ?: existing.profileUrl,
                    updatedAt = Instant.now(),
                ),
            )
        }
    }
}
