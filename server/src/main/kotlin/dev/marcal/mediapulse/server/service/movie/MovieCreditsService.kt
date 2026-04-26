package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieCreditsBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieCreditsSyncResponse
import dev.marcal.mediapulse.server.api.movies.MoviePersonCreditDto
import dev.marcal.mediapulse.server.api.movies.MoviePersonLinkRequest
import dev.marcal.mediapulse.server.api.movies.MoviePersonSuggestionDto
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditCandidateDto
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditCandidateGroupDto
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditCandidatesResponse
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditImportRequest
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieCreditType
import dev.marcal.mediapulse.server.model.movie.MoviePerson
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCreditsCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MoviePersonRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
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
    private val moviePersonRepository: MoviePersonRepository,
    private val movieCreditAssignmentRepository: MovieCreditAssignmentRepository,
    private val movieCreditsCrudRepository: MovieCreditsCrudRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualMovieCatalogService: ManualMovieCatalogService,
    private val transactionTemplate: TransactionTemplate,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    private val relevantCrewJobs =
        setOf(
            "Director",
            "Writer",
            "Screenplay",
            "Story",
            "Editor",
            "Producer",
            "Director of Photography",
            "Original Music Composer",
        )

    private val castLimit = 10

    @Transactional
    fun syncFromTmdb(movieId: Long): MovieCreditsSyncResponse = syncFromTmdbInternal(movieId)

    @Transactional(readOnly = true)
    fun searchPeople(
        query: String,
        limit: Int,
    ): List<MoviePersonSuggestionDto> = movieQueryRepository.searchMoviePeople(query, limit.coerceIn(1, 20))

    @Transactional
    fun fetchTmdbCandidates(movieId: Long): MovieTmdbCreditCandidatesResponse {
        val movie = requireMovie(movieId)
        val credits = requireTmdbMovieCredits(movieId)
        val currentCredits = movieQueryRepository.getMoviePeople(movieId)
        val linkedKeys = currentCredits.mapTo(linkedSetOf(), ::creditKey)

        val extraCast =
            credits.cast
                .sortedBy { it.order ?: Int.MAX_VALUE }
                .filter { (it.order ?: Int.MAX_VALUE) >= castLimit }
        val extraCrew = credits.crew.filter { (it.job ?: "") !in relevantCrewJobs }

        val reconciledCount =
            reconcileExistingLocalCredits(
                movie = movie,
                cast = extraCast,
                crew = extraCrew,
                linkedKeys = linkedKeys,
            )

        val groups =
            listOfNotNull(
                extraCast
                    .mapNotNull { credit ->
                        buildTmdbCandidate(linkedKeys, credit)
                    }.takeIf { it.isNotEmpty() }
                    ?.let { items ->
                        MovieTmdbCreditCandidateGroupDto(
                            id = "cast",
                            title = "Mais elenco do TMDb",
                            items = items,
                        )
                    },
                extraCrew
                    .mapNotNull { credit ->
                        buildTmdbCandidate(linkedKeys, credit)
                    }.takeIf { it.isNotEmpty() }
                    ?.let { items ->
                        MovieTmdbCreditCandidateGroupDto(
                            id = "crew",
                            title = "Mais equipe do TMDb",
                            items = items,
                        )
                    },
            )

        return MovieTmdbCreditCandidatesResponse(
            movieId = movie.id,
            reconciledCount = reconciledCount,
            candidateCount = groups.sumOf { it.items.size },
            groups = groups,
        )
    }

    @Transactional
    fun linkExistingPerson(
        movieId: Long,
        request: MoviePersonLinkRequest,
    ): MoviePersonCreditDto {
        val movie = requireMovie(movieId)
        val person =
            moviePersonRepository.findById(request.personId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Movie person not found")
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
                        job = roleLabel ?: "Writer",
                    )
                "CAST" ->
                    MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                        movieId = movie.id,
                        personId = person.id,
                        creditType = MovieCreditType.CAST,
                        characterName = roleLabel ?: "Elenco",
                    )
                "OTHER" ->
                    MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                        movieId = movie.id,
                        personId = person.id,
                        creditType = MovieCreditType.CREW,
                        department = "Manual",
                        job = roleLabel ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "roleLabel é obrigatório"),
                    )
                else -> throw ResponseStatusException(HttpStatus.BAD_REQUEST, "group inválido")
            }

        movieCreditAssignmentRepository.upsert(credit)
        return findPersistedCredit(movieId, person.id, credit)
    }

    @Transactional
    fun importTmdbCredit(
        movieId: Long,
        request: MovieTmdbCreditImportRequest,
    ): MoviePersonCreditDto {
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
        return findPersistedCredit(movieId, credit.personId, credit)
    }

    @Transactional
    fun syncFromTmdbIfLinked(movieId: Long) {
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

        val castCredits =
            credits.cast
                .sortedBy { it.order ?: Int.MAX_VALUE }
                .filter { credit ->
                    val inPrimaryCut = (credit.order ?: Int.MAX_VALUE) < castLimit
                    inPrimaryCut || personExistsLocally(credit.tmdbId)
                }.map { credit ->
                    val person = upsertPerson(credit.tmdbId, credit.name, credit.profilePath)
                    MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                        movieId = movie.id,
                        personId = person.id,
                        creditType = MovieCreditType.CAST,
                        characterName = credit.character ?: "",
                        billingOrder = credit.order,
                    )
                }

        val crewCredits =
            credits.crew
                .filter { credit ->
                    (credit.job in relevantCrewJobs) || personExistsLocally(credit.tmdbId)
                }.distinctBy { listOf(it.tmdbId, it.job ?: "", it.department ?: "") }
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

        return MovieCreditsSyncResponse(
            movieId = movieId,
            syncedCount = resolvedCredits.size,
            visibleCount = movieQueryRepository.getMoviePeople(movieId).size,
        ).also {
            movieCreditsCrudRepository.markCreditsSynced(movieId)
        }
    }

    private fun reconcileExistingLocalCredits(
        movie: Movie,
        cast: List<TmdbApiClient.TmdbMovieCastCredit>,
        crew: List<TmdbApiClient.TmdbMovieCrewCredit>,
        linkedKeys: MutableSet<String>,
    ): Int {
        var reconciled = 0

        cast.forEach { credit ->
            val person = moviePersonRepository.findByTmdbId(credit.tmdbId) ?: return@forEach
            val request =
                MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                    movieId = movie.id,
                    personId = person.id,
                    creditType = MovieCreditType.CAST,
                    characterName = credit.character ?: "",
                    billingOrder = credit.order,
                )
            val key = creditKey(person.tmdbId, request.creditType, request.job, request.characterName)
            if (linkedKeys.add(key)) {
                movieCreditAssignmentRepository.upsert(request)
                reconciled++
            }
        }

        crew.forEach { credit ->
            val person = moviePersonRepository.findByTmdbId(credit.tmdbId) ?: return@forEach
            val request =
                MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                    movieId = movie.id,
                    personId = person.id,
                    creditType = MovieCreditType.CREW,
                    department = credit.department ?: "",
                    job = credit.job ?: "",
                )
            val key = creditKey(person.tmdbId, request.creditType, request.job, request.characterName)
            if (linkedKeys.add(key)) {
                movieCreditAssignmentRepository.upsert(request)
                reconciled++
            }
        }

        return reconciled
    }

    private fun buildTmdbCandidate(
        linkedKeys: Set<String>,
        credit: TmdbApiClient.TmdbMovieCastCredit,
    ): MovieTmdbCreditCandidateDto? {
        val key = creditKey(credit.tmdbId, MovieCreditType.CAST, "", credit.character ?: "")
        if (key in linkedKeys || moviePersonRepository.findByTmdbId(credit.tmdbId) != null) {
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
        )
    }

    private fun buildTmdbCandidate(
        linkedKeys: Set<String>,
        credit: TmdbApiClient.TmdbMovieCrewCredit,
    ): MovieTmdbCreditCandidateDto? {
        val key = creditKey(credit.tmdbId, MovieCreditType.CREW, credit.job ?: "", "")
        if (key in linkedKeys || moviePersonRepository.findByTmdbId(credit.tmdbId) != null) {
            return null
        }

        return MovieTmdbCreditCandidateDto(
            personTmdbId = credit.tmdbId,
            name = credit.name,
            profileUrl = credit.profilePath?.let(manualMovieCatalogService::buildTmdbImageUrl),
            creditType = MovieCreditType.CREW.toDto(),
            department = credit.department,
            job = credit.job,
            characterName = null,
            billingOrder = null,
            roleLabel = credit.job?.takeIf { it.isNotBlank() } ?: credit.department?.takeIf { it.isNotBlank() } ?: "Equipe",
        )
    }

    private fun findPersistedCredit(
        movieId: Long,
        personId: Long,
        credit: MovieCreditAssignmentRepository.UpsertMovieCreditRequest,
    ): MoviePersonCreditDto =
        movieQueryRepository.getMoviePeople(movieId).firstOrNull {
            it.personId == personId &&
                it.creditType == credit.creditType.toDto() &&
                (it.job ?: "") == credit.job &&
                (it.characterName ?: "") == credit.characterName
        } ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie person credit not found")

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

    private fun personExistsLocally(tmdbId: String): Boolean = moviePersonRepository.findByTmdbId(tmdbId) != null

    private fun upsertPerson(
        tmdbId: String,
        rawName: String,
        profilePath: String?,
    ): MoviePerson {
        val name = rawName.trim().replace("\\s+".toRegex(), " ")
        val normalizedName = name.lowercase()
        val profileUrl = profilePath?.let(manualMovieCatalogService::buildTmdbImageUrl)
        val slug = SlugTextUtil.normalize("$name $tmdbId", maxLength = 80)
        val existing = moviePersonRepository.findByTmdbId(tmdbId)
        return if (existing == null) {
            moviePersonRepository.save(
                MoviePerson(
                    tmdbId = tmdbId,
                    name = name,
                    normalizedName = normalizedName,
                    slug = slug,
                    profileUrl = profileUrl,
                ),
            )
        } else {
            moviePersonRepository.save(
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

    private fun creditKey(dto: MoviePersonCreditDto): String =
        creditKey(dto.tmdbId, MovieCreditType.valueOf(dto.creditType.name), dto.job ?: "", dto.characterName ?: "")

    private fun creditKey(
        tmdbId: String,
        creditType: MovieCreditType,
        job: String,
        characterName: String,
    ): String = listOf(tmdbId, creditType.name, job, characterName).joinToString("|")

    private fun MovieCreditType.toDto(): dev.marcal.mediapulse.server.api.movies.MovieCreditTypeDto =
        dev.marcal.mediapulse.server.api.movies.MovieCreditTypeDto
            .valueOf(name)
}
