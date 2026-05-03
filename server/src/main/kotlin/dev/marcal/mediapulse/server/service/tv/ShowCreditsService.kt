package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowCreditsBatchSyncResponse
import dev.marcal.mediapulse.server.api.shows.ShowCreditsSyncResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.MovieCreditType
import dev.marcal.mediapulse.server.model.person.Person
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.TvShowQueryRepository
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
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
    private val externalIdentifierRepository: ExternalIdentifierRepository,
    private val personRepository: PersonRepository,
    private val showCreditAssignmentRepository: ShowCreditAssignmentRepository,
    private val showCreditsCrudRepository: ShowCreditsCrudRepository,
    private val tvShowQueryRepository: TvShowQueryRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualShowCatalogService: ManualShowCatalogService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val relevantCrewJobs =
        setOf(
            "Director",
            "Writer",
            "Screenplay",
            "Story Editor",
            "Executive Producer",
            "Producer",
            "Original Music Composer",
        )

    private val castLimit = 12

    @Transactional
    fun syncFromTmdb(showId: Long): ShowCreditsSyncResponse = syncFromTmdbInternal(showId)

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
        val tmdbId = requireTmdbId(show.id)
        val credits =
            tmdbApiClient.fetchShowCredits(tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb show credits not found")

        val castCredits =
            credits.cast
                .sortedBy { it.order ?: Int.MAX_VALUE }
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

        val crewCredits =
            credits.crew
                .filter { it.job in relevantCrewJobs }
                .distinctBy { listOf(it.tmdbId, it.job ?: "", it.department ?: "") }
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

        return ShowCreditsSyncResponse(
            showId = show.id,
            syncedCount = resolvedCredits.size,
            visibleCount = tvShowQueryRepository.getShowPeople(show.id).size,
        ).also {
            showCreditsCrudRepository.markCreditsSynced(show.id)
        }
    }

    private fun requireShow(showId: Long): TvShow =
        tvShowRepository.findById(showId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")
        }

    private fun requireTmdbId(showId: Long): String =
        externalIdentifierRepository
            .findFirstByEntityTypeAndProviderAndEntityId(
                entityType = EntityType.SHOW,
                provider = Provider.TMDB,
                entityId = showId,
            )?.externalId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Série sem vínculo TMDb")

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
