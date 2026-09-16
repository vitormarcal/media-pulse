package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.CreditBatchImportRequest
import dev.marcal.mediapulse.server.api.movies.CreditBatchImportResponse
import dev.marcal.mediapulse.server.api.movies.CreditBatchImportResultDto
import dev.marcal.mediapulse.server.api.movies.MovieCreditsBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieCreditsSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditCandidateDto
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditCandidateGroupDto
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditCandidatesResponse
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditImportRequest
import dev.marcal.mediapulse.server.api.movies.PersonCreditDto
import dev.marcal.mediapulse.server.api.movies.PersonLinkRequest
import dev.marcal.mediapulse.server.api.movies.PersonSuggestionDto
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieCreditType
import dev.marcal.mediapulse.server.model.person.Person
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.PersonCleanupRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCreditsCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.PersonRepository
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class MovieCreditsService(
    private val movieRepository: MovieRepository,
    private val movieQueryRepository: MovieQueryRepository,
    private val personRepository: PersonRepository,
    private val movieCreditAssignmentRepository: MovieCreditAssignmentRepository,
    private val movieCreditsCrudRepository: MovieCreditsCrudRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualMovieCatalogService: ManualMovieCatalogService,
    private val transactionTemplate: TransactionTemplate,
    private val personCleanupRepository: PersonCleanupRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val relevantCrewJobs =
        setOf(
            "Director",
            "Writer",
            "Screenplay",
            "Story",
        )

    private val castLimit = 12
    private val crewLimit = 6

    @Transactional
    fun syncFromTmdb(movieId: Long): MovieCreditsSyncResponse =
        syncFromTmdbInternal(movieId).also { movieCreditsCrudRepository.clearCurated(movieId) }

    @Transactional(readOnly = true)
    fun searchPeople(
        query: String,
        limit: Int,
    ): List<PersonSuggestionDto> = movieQueryRepository.searchPeople(query, limit.coerceIn(1, 20))

    @Transactional(readOnly = true)
    fun fetchTmdbCandidates(movieId: Long): MovieTmdbCreditCandidatesResponse {
        val movie = requireMovie(movieId)
        val credits = requireTmdbMovieCredits(movieId)
        val currentCredits = movieQueryRepository.getMoviePeople(movieId)
        val linkedKeys = currentCredits.mapTo(linkedSetOf(), ::categoryKey)
        val cast = credits.cast.sortedWith(compareBy({ it.order ?: Int.MAX_VALUE }, { it.name }, { it.tmdbId })).distinctBy { it.tmdbId }
        val directors = credits.crew.filter { it.job == "Director" }.distinctBy { it.tmdbId }
        val writers = credits.crew.filter { it.job in writerJobs }.distinctBy { it.tmdbId }

        val groups =
            listOf(
                candidateGroup("cast", "Elenco do TMDb", cast.mapNotNull { buildTmdbCandidate(linkedKeys, it) }),
                candidateGroup("directors", "Direção do TMDb", directors.mapNotNull { buildTmdbCandidate(linkedKeys, it) }),
                candidateGroup("writers", "Roteiro do TMDb", writers.mapNotNull { buildTmdbCandidate(linkedKeys, it) }),
            ).filter { it.items.isNotEmpty() }

        return MovieTmdbCreditCandidatesResponse(
            movieId = movie.id,
            reconciledCount = 0,
            candidateCount = groups.sumOf { it.items.size },
            groups = groups,
        )
    }

    @Transactional
    fun linkExistingPerson(
        movieId: Long,
        request: PersonLinkRequest,
    ): PersonCreditDto {
        val movie = requireMovie(movieId)
        val person =
            personRepository.findById(request.personId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found")
            }

        val group = request.group.trim().uppercase()
        val roleLabel =
            request.roleLabel
                ?.trim()
                ?.replace("\\s+".toRegex(), " ")
                ?.ifBlank { null }
        val credit =
            when (group) {
                "DIRECTORS" ->
                    MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                        movieId = movie.id,
                        personId = person.id,
                        creditType = MovieCreditType.CREW,
                        department = "Directing",
                        job = "Director",
                    )
                "WRITERS" ->
                    MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                        movieId = movie.id,
                        personId = person.id,
                        creditType = MovieCreditType.CREW,
                        department = "Writing",
                        job = "Writer",
                    )
                "CAST" ->
                    MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                        movieId = movie.id,
                        personId = person.id,
                        creditType = MovieCreditType.CAST,
                        characterName = roleLabel ?: "Elenco",
                    )
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "group inválido")
            }

        movieCreditAssignmentRepository.upsert(credit)
        movieCreditsCrudRepository.markCurated(movie.id)
        return findPersistedCredit(movieId, person.id, credit)
    }

    @Transactional
    fun importTmdbCredit(
        movieId: Long,
        request: MovieTmdbCreditImportRequest,
    ): PersonCreditDto {
        requireMovie(movieId)
        val credits = requireTmdbMovieCredits(movieId)

        val credit =
            when (request.creditType) {
                dev.marcal.mediapulse.server.api.movies.MovieCreditTypeDto.CAST -> {
                    val match =
                        credits.cast.firstOrNull {
                            it.tmdbId == request.personTmdbId &&
                                (it.character ?: "") == (request.characterName ?: "") &&
                                it.order == request.billingOrder
                        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb cast credit not found")
                    val person = upsertPerson(match.tmdbId, match.name, match.profilePath)
                    MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                        movieId = movieId,
                        personId = person.id,
                        creditType = MovieCreditType.CAST,
                        characterName = match.character ?: "",
                        billingOrder = match.order,
                    )
                }
                dev.marcal.mediapulse.server.api.movies.MovieCreditTypeDto.CREW -> {
                    val match =
                        credits.crew.firstOrNull {
                            it.tmdbId == request.personTmdbId &&
                                (it.department ?: "") == (request.department ?: "") &&
                                (it.job ?: "") == (request.job ?: "")
                        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb crew credit not found")
                    if (match.job !in relevantCrewJobs) {
                        throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Crédito de equipe fora do recorte audiovisual")
                    }
                    val person = upsertPerson(match.tmdbId, match.name, match.profilePath)
                    MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                        movieId = movieId,
                        personId = person.id,
                        creditType = MovieCreditType.CREW,
                        department = match.department ?: "",
                        job = match.job ?: "",
                    )
                }
            }

        movieCreditAssignmentRepository.upsert(credit)
        movieCreditsCrudRepository.markCurated(movieId)
        return findPersistedCredit(movieId, credit.personId, credit)
    }

    fun importTmdbCredits(
        movieId: Long,
        request: CreditBatchImportRequest<MovieTmdbCreditImportRequest>,
    ): CreditBatchImportResponse {
        val results =
            request.items.distinctBy(::importKey).map { item ->
                val success = runCatching { transactionTemplate.execute { importTmdbCredit(movieId, item) } }.isSuccess
                CreditBatchImportResultDto(importKey(item), success)
            }
        return CreditBatchImportResponse(results.count { it.success }, results.count { !it.success }, results)
    }

    @Transactional
    fun removeCredit(
        movieId: Long,
        personId: Long,
        rawCategory: String,
    ) {
        requireMovie(movieId)
        val category = normalizeCategory(rawCategory)
        if (movieCreditAssignmentRepository.deleteCategory(movieId, personId, category) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Crédito não encontrado")
        }
        movieCreditsCrudRepository.markCurated(movieId)
        personCleanupRepository.deleteIfOrphanAndNotFavorite(personId)
    }

    @Transactional
    fun syncFromTmdbIfLinked(movieId: Long) {
        if (requireMovie(movieId).creditsCuratedAt != null) return
        val hasTmdbLink =
            movieQueryRepository
                .getMovieDetails(movieId)
                .externalIds
                .any { it.provider == "TMDB" }

        if (hasTmdbLink) {
            syncFromTmdbInternal(movieId)
        }
    }

    fun syncAllFromTmdb(limit: Int = 100): MovieCreditsBatchSyncResponse {
        val requestedLimit = limit.coerceIn(1, 1000)
        val pendingTotal = movieCreditsCrudRepository.countPendingTmdbSyncCandidates()
        val candidates = movieCreditsCrudRepository.findTmdbSyncCandidates(requestedLimit)
        var synced = 0
        var failed = 0
        var processed = 0

        logger.info(
            "Movie credits TMDb batch sync started | requestedLimit={} | pendingTotal={} | selectedCandidates={}",
            requestedLimit,
            pendingTotal,
            candidates.size,
        )

        candidates.forEach { candidate ->
            runCatching {
                transactionTemplate.execute {
                    syncFromTmdbInternal(candidate.movieId)
                } ?: error("Batch credit sync transaction returned null")
            }.onSuccess {
                synced++
            }.onFailure { ex ->
                failed++
                logger.warn(
                    "Failed to sync movie credits from TMDb in batch | movieId={} tmdbId={}",
                    candidate.movieId,
                    candidate.tmdbId,
                    ex,
                )
            }

            processed++
            logger.info(
                "Movie credits TMDb batch sync progress | processed={} | synced={} | failed={} | remainingInBatch={} | remainingPendingEstimate={}",
                processed,
                synced,
                failed,
                (candidates.size - processed).coerceAtLeast(0),
                (pendingTotal - processed).coerceAtLeast(0),
            )
        }

        return MovieCreditsBatchSyncResponse(
            requestedLimit = requestedLimit,
            candidates = candidates.size,
            processed = processed,
            synced = synced,
            failed = failed,
        ).also { response ->
            logger.info(
                "Movie credits TMDb batch sync finished | requestedLimit={} | pendingTotal={} | candidates={} | processed={} | synced={} | failed={}",
                requestedLimit,
                pendingTotal,
                response.candidates,
                response.processed,
                response.synced,
                response.failed,
            )
        }
    }

    private fun syncFromTmdbInternal(movieId: Long): MovieCreditsSyncResponse {
        val movie = requireMovie(movieId)
        val credits = requireTmdbMovieCredits(movieId)
        val previousPersonIds = movieQueryRepository.getMoviePeople(movieId).map { it.personId }.toSet()

        val castCredits =
            credits.cast
                .sortedWith(compareBy({ it.order ?: Int.MAX_VALUE }, { it.name }, { it.tmdbId }))
                .distinctBy { it.tmdbId }
                .take(castLimit)
                .map { credit ->
                    val person = upsertPerson(credit.tmdbId, credit.name, credit.profilePath)
                    MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                        movieId = movie.id,
                        personId = person.id,
                        creditType = MovieCreditType.CAST,
                        characterName = credit.character ?: "",
                        billingOrder = credit.order,
                    )
                }

        val selectedCrew =
            credits.crew
                .filter { it.job == "Director" }
                .distinctBy { it.tmdbId }
                .take(crewLimit) +
                credits.crew
                    .filter { it.job in writerJobs }
                    .distinctBy { it.tmdbId }
                    .take(crewLimit)
        val crewCredits =
            selectedCrew
                .map { credit ->
                    val person = upsertPerson(credit.tmdbId, credit.name, credit.profilePath)
                    MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                        movieId = movie.id,
                        personId = person.id,
                        creditType = MovieCreditType.CREW,
                        department = credit.department ?: "",
                        job = credit.job ?: "",
                    )
                }

        val resolvedCredits =
            (castCredits + crewCredits)
                .distinctBy { listOf(it.personId, it.creditType.name, it.job, it.characterName) }

        movieCreditAssignmentRepository.replaceForMovie(movie.id, resolvedCredits)
        previousPersonIds.forEach(personCleanupRepository::deleteIfOrphanAndNotFavorite)

        return MovieCreditsSyncResponse(
            movieId = movieId,
            syncedCount = resolvedCredits.size,
            visibleCount = movieQueryRepository.getMoviePeople(movieId).size,
            curated = false,
        ).also {
            movieCreditsCrudRepository.markCreditsSynced(movieId)
        }
    }

    private fun buildTmdbCandidate(
        linkedKeys: Set<String>,
        credit: TmdbApiClient.TmdbMovieCastCredit,
    ): MovieTmdbCreditCandidateDto? {
        val key = categoryKey(credit.tmdbId, "CAST")
        if (key in linkedKeys) {
            return null
        }

        return MovieTmdbCreditCandidateDto(
            personTmdbId = credit.tmdbId,
            name = credit.name,
            profileUrl = credit.profilePath?.let(manualMovieCatalogService::buildTmdbImageUrl),
            creditType = MovieCreditType.CAST.toDto(),
            department = null,
            job = null,
            characterName = credit.character,
            billingOrder = credit.order,
            roleLabel = credit.character?.takeIf { it.isNotBlank() } ?: "Elenco",
            tmdbUrl = "https://www.themoviedb.org/person/${credit.tmdbId}",
        )
    }

    private fun buildTmdbCandidate(
        linkedKeys: Set<String>,
        credit: TmdbApiClient.TmdbMovieCrewCredit,
    ): MovieTmdbCreditCandidateDto? {
        val category = if (credit.job == "Director") "DIRECTING" else "WRITING"
        if (categoryKey(credit.tmdbId, category) in linkedKeys) return null
        return MovieTmdbCreditCandidateDto(
            personTmdbId = credit.tmdbId,
            name = credit.name,
            profileUrl = credit.profilePath?.let(manualMovieCatalogService::buildTmdbImageUrl),
            creditType = MovieCreditType.CREW.toDto(),
            department = credit.department,
            job = credit.job,
            characterName = null,
            billingOrder = null,
            roleLabel = if (category == "DIRECTING") "Direção" else "Roteiro",
            tmdbUrl = "https://www.themoviedb.org/person/${credit.tmdbId}",
        )
    }

    private fun candidateGroup(
        id: String,
        title: String,
        items: List<MovieTmdbCreditCandidateDto>,
    ) = MovieTmdbCreditCandidateGroupDto(id = id, title = title, items = items)

    private fun normalizeCategory(raw: String): String =
        raw.trim().uppercase().takeIf { it in setOf("CAST", "DIRECTING", "WRITING") }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "category inválida")

    private fun findPersistedCredit(
        movieId: Long,
        personId: Long,
        credit: MovieCreditAssignmentRepository.UpsertMovieCreditRequest,
    ): PersonCreditDto =
        movieQueryRepository.getMoviePeople(movieId).firstOrNull {
            it.personId == personId &&
                it.creditType == credit.creditType.toDto() &&
                (it.job ?: "") == credit.job &&
                (it.characterName ?: "") == credit.characterName
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Person credit not found")

    private fun requireMovie(movieId: Long): Movie =
        movieRepository.findById(movieId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found")
        }

    private fun requireTmdbId(movieId: Long): String =
        movieQueryRepository
            .getMovieDetails(movieId)
            .externalIds
            .firstOrNull { it.provider == "TMDB" }
            ?.externalId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Filme sem vínculo TMDb")

    private fun requireTmdbMovieCredits(movieId: Long): TmdbApiClient.TmdbMovieCredits {
        val tmdbId = requireTmdbId(movieId)
        return tmdbApiClient.fetchMovieCredits(tmdbId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb movie credits not found")
    }

    private fun upsertPerson(
        tmdbId: String,
        rawName: String,
        profilePath: String?,
    ): Person {
        val name = rawName.trim().replace("\\s+".toRegex(), " ")
        val normalizedName = name.lowercase()
        val profileUrl = profilePath?.let(manualMovieCatalogService::buildTmdbImageUrl)
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

    private val writerJobs = setOf("Writer", "Screenplay", "Story")

    private fun importKey(request: MovieTmdbCreditImportRequest): String =
        listOf(request.personTmdbId, request.creditType.name, request.job ?: "", request.characterName ?: "").joinToString("|")

    private fun categoryKey(dto: PersonCreditDto): String =
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

    private fun MovieCreditType.toDto(): dev.marcal.mediapulse.server.api.movies.MovieCreditTypeDto =
        dev.marcal.mediapulse.server.api.movies.MovieCreditTypeDto
            .valueOf(name)
}
