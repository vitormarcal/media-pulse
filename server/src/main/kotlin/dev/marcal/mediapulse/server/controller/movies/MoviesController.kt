package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.ExistingMovieWatchCreateRequest
import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchCreateResponse
import dev.marcal.mediapulse.server.api.movies.MovieCompaniesBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieCompaniesSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieCompanyDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieCreditsBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieCreditsSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MoviePersonCreditDto
import dev.marcal.mediapulse.server.api.movies.MoviePersonDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MoviePersonLinkRequest
import dev.marcal.mediapulse.server.api.movies.MoviePersonSuggestionDto
import dev.marcal.mediapulse.server.api.movies.MovieTermCreateRequest
import dev.marcal.mediapulse.server.api.movies.MovieTermDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieTermDto
import dev.marcal.mediapulse.server.api.movies.MovieTermSuggestionDto
import dev.marcal.mediapulse.server.api.movies.MovieTermVisibilityRequest
import dev.marcal.mediapulse.server.api.movies.MovieTermsBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieTermsSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditCandidatesResponse
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditImportRequest
import dev.marcal.mediapulse.server.api.movies.MoviesByYearResponse
import dev.marcal.mediapulse.server.api.movies.MoviesLibraryResponse
import dev.marcal.mediapulse.server.api.movies.MoviesRecentResponse
import dev.marcal.mediapulse.server.api.movies.MoviesSearchResponse
import dev.marcal.mediapulse.server.api.movies.MoviesStatsResponse
import dev.marcal.mediapulse.server.api.movies.MoviesSummaryResponse
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.service.movie.ExistingMovieWatchCreateFlowService
import dev.marcal.mediapulse.server.service.movie.MovieCompaniesService
import dev.marcal.mediapulse.server.service.movie.MovieCreditsService
import dev.marcal.mediapulse.server.service.movie.MovieTermsService
import dev.marcal.mediapulse.server.service.movie.MovieWatchRemovalService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.min

@RestController
@RequestMapping("/api/movies")
class MoviesController(
    private val repository: MovieQueryRepository,
    private val existingMovieWatchCreateFlowService: ExistingMovieWatchCreateFlowService,
    private val movieWatchRemovalService: MovieWatchRemovalService,
    private val movieTermsService: MovieTermsService,
    private val movieCompaniesService: MovieCompaniesService,
    private val movieCreditsService: MovieCreditsService,
) {
    @GetMapping("/library")
    fun library(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): MoviesLibraryResponse = repository.library(limit, cursor)

    @GetMapping("/recent")
    fun recent(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): MoviesRecentResponse = repository.recent(limit, cursor)

    @GetMapping("/{movieId}")
    fun details(
        @PathVariable movieId: Long,
    ): MovieDetailsResponse = repository.getMovieDetails(movieId)

    @GetMapping("/slug/{slug}")
    fun detailsBySlug(
        @PathVariable slug: String,
    ): MovieDetailsResponse = repository.getMovieDetailsBySlug(slug)

    @GetMapping("/people/{slug}")
    fun personDetails(
        @PathVariable slug: String,
    ): MoviePersonDetailsResponse = repository.getMoviePersonDetails(slug)

    @GetMapping("/companies/{slug}")
    fun companyDetails(
        @PathVariable slug: String,
    ): MovieCompanyDetailsResponse = repository.getMovieCompanyDetails(slug)

    @GetMapping("/people/search")
    fun searchPeople(
        @RequestParam q: String,
        @RequestParam(defaultValue = "8") limit: Int,
    ): List<MoviePersonSuggestionDto> = movieCreditsService.searchPeople(q, normalizeLimit("limit", limit))

    @GetMapping("/terms/{kind}/{slug}")
    fun termDetails(
        @PathVariable kind: String,
        @PathVariable slug: String,
    ): MovieTermDetailsResponse {
        val normalizedKind = kind.trim().uppercase()
        if (normalizedKind != "GENRE" && normalizedKind != "TAG") {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "kind inválido")
        }
        return repository.getMovieTermDetails(normalizedKind, slug)
    }

    @GetMapping("/terms/search")
    fun searchTerms(
        @RequestParam q: String,
        @RequestParam kind: String,
        @RequestParam(defaultValue = "8") limit: Int,
    ): List<MovieTermSuggestionDto> {
        val normalizedKind = kind.trim().uppercase()
        if (normalizedKind != "GENRE" && normalizedKind != "TAG") {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "kind inválido")
        }
        return repository.searchMovieTerms(q, normalizedKind, normalizeLimit("limit", limit))
    }

    @PostMapping("/{movieId}/terms/sync-tmdb")
    fun syncTermsFromTmdb(
        @PathVariable movieId: Long,
    ): MovieTermsSyncResponse = movieTermsService.syncFromTmdb(movieId)

    @PostMapping("/terms/sync-tmdb")
    fun syncAllTermsFromTmdb(
        @RequestParam(defaultValue = "100") limit: Int,
    ): MovieTermsBatchSyncResponse = movieTermsService.syncAllFromTmdb(normalizeLimit("limit", limit))

    @PostMapping("/{movieId}/companies/sync-tmdb")
    fun syncCompaniesFromTmdb(
        @PathVariable movieId: Long,
    ): MovieCompaniesSyncResponse = movieCompaniesService.syncFromTmdb(movieId)

    @PostMapping("/companies/sync-tmdb")
    fun syncAllCompaniesFromTmdb(
        @RequestParam(defaultValue = "100") limit: Int,
    ): MovieCompaniesBatchSyncResponse = movieCompaniesService.syncAllFromTmdb(normalizeLimit("limit", limit))

    @PostMapping("/{movieId}/credits/sync-tmdb")
    fun syncCreditsFromTmdb(
        @PathVariable movieId: Long,
    ): MovieCreditsSyncResponse = movieCreditsService.syncFromTmdb(movieId)

    @PostMapping("/credits/sync-tmdb")
    fun syncAllCreditsFromTmdb(
        @RequestParam(defaultValue = "100") limit: Int,
    ): MovieCreditsBatchSyncResponse = movieCreditsService.syncAllFromTmdb(normalizeLimit("limit", limit))

    @GetMapping("/{movieId}/credits/tmdb-candidates")
    fun movieTmdbCreditCandidates(
        @PathVariable movieId: Long,
    ): MovieTmdbCreditCandidatesResponse = movieCreditsService.fetchTmdbCandidates(movieId)

    @PostMapping("/{movieId}/credits/from-tmdb")
    fun importMovieTmdbCredit(
        @PathVariable movieId: Long,
        @RequestBody request: MovieTmdbCreditImportRequest,
    ): MoviePersonCreditDto = movieCreditsService.importTmdbCredit(movieId, request)

    @PostMapping("/{movieId}/people")
    fun linkExistingPerson(
        @PathVariable movieId: Long,
        @RequestBody request: MoviePersonLinkRequest,
    ): MoviePersonCreditDto = movieCreditsService.linkExistingPerson(movieId, request)

    @PostMapping("/{movieId}/terms")
    fun addTerm(
        @PathVariable movieId: Long,
        @RequestBody request: MovieTermCreateRequest,
    ): MovieTermDto = movieTermsService.addTerm(movieId, request)

    @PostMapping("/{movieId}/terms/{termId}/visibility")
    fun updateMovieTermVisibility(
        @PathVariable movieId: Long,
        @PathVariable termId: Long,
        @RequestBody request: MovieTermVisibilityRequest,
    ): MovieTermDto = movieTermsService.updateMovieVisibility(movieId, termId, request.hidden)

    @PostMapping("/terms/{termId}/visibility")
    fun updateGlobalTermVisibility(
        @PathVariable termId: Long,
        @RequestBody request: MovieTermVisibilityRequest,
    ): MovieTermDto = movieTermsService.updateGlobalVisibility(termId, request.hidden)

    @PostMapping("/{movieId}/watches")
    fun addWatch(
        @PathVariable movieId: Long,
        @RequestBody request: ExistingMovieWatchCreateRequest,
    ): ManualMovieWatchCreateResponse = existingMovieWatchCreateFlowService.execute(movieId, request.watchedAt)

    @DeleteMapping("/{movieId}/watches/{watchId}")
    fun deleteWatch(
        @PathVariable movieId: Long,
        @PathVariable watchId: Long,
    ) {
        movieWatchRemovalService.remove(movieId, watchId)
    }

    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): MoviesSearchResponse = repository.search(q, limit)

    @GetMapping("/summary")
    fun summary(
        @RequestParam(defaultValue = "month") range: String,
        @RequestParam(required = false) start: Instant?,
        @RequestParam(required = false) end: Instant?,
    ): MoviesSummaryResponse {
        val (resolvedStart, resolvedEnd) = resolveRange(range, start, end)
        return repository.summary(resolvedStart, resolvedEnd)
    }

    @GetMapping("/stats")
    fun stats(): MoviesStatsResponse = repository.stats()

    @GetMapping("/year/{year}")
    fun byYear(
        @PathVariable year: Int,
        @RequestParam(defaultValue = "200") limitWatched: Int,
        @RequestParam(defaultValue = "200") limitUnwatched: Int,
    ): MoviesByYearResponse {
        val validatedYear = validateYear(year)
        val resolvedLimitWatched = normalizeLimit("limitWatched", limitWatched)
        val resolvedLimitUnwatched = normalizeLimit("limitUnwatched", limitUnwatched)
        val (start, end) = yearRange(validatedYear)
        return repository.byYear(
            year = validatedYear,
            start = start,
            end = end,
            limitWatched = resolvedLimitWatched,
            limitUnwatched = resolvedLimitUnwatched,
        )
    }

    private fun resolveRange(
        range: String,
        start: Instant?,
        end: Instant?,
    ): Pair<Instant, Instant> =
        when (range.lowercase()) {
            "month" -> {
                val e = Instant.now()
                val s = e.minus(Duration.ofDays(30))
                s to e
            }
            "year" -> {
                val e = Instant.now()
                val s = e.minus(Duration.ofDays(365))
                s to e
            }
            "custom" -> {
                if (start == null || end == null) {
                    throw IllegalArgumentException("range inválido")
                }
                start to end
            }
            else -> throw IllegalArgumentException("range inválido")
        }

    private fun validateYear(year: Int): Int {
        val maxYear = LocalDate.now(ZoneOffset.UTC).year + 1
        if (year < 1900 || year > maxYear) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "year inválido")
        }
        return year
    }

    private fun normalizeLimit(
        name: String,
        value: Int,
    ): Int {
        if (value < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$name deve ser >= 1")
        }
        return min(value, 1000)
    }

    private fun yearRange(year: Int): Pair<Instant, Instant> {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val end = LocalDate.of(year, 12, 31).atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
        return start to end
    }
}
