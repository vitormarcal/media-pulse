package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.MovieCompaniesBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieCompanyDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieCompanyTypeDto
import dev.marcal.mediapulse.server.api.movies.MovieCreditsBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieListAttachRequest
import dev.marcal.mediapulse.server.api.movies.MovieListCreateRequest
import dev.marcal.mediapulse.server.api.movies.MovieListDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieListOrderUpdateRequest
import dev.marcal.mediapulse.server.api.movies.MovieListSummaryDto
import dev.marcal.mediapulse.server.api.movies.MoviePersonDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MoviePersonLinkRequest
import dev.marcal.mediapulse.server.api.movies.MoviePersonSuggestionDto
import dev.marcal.mediapulse.server.api.movies.MovieTermDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieTermKindDto
import dev.marcal.mediapulse.server.api.movies.MovieTermSourceDto
import dev.marcal.mediapulse.server.api.movies.MovieTermSuggestionDto
import dev.marcal.mediapulse.server.api.movies.MovieTermsBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditCandidatesResponse
import dev.marcal.mediapulse.server.api.movies.MovieTmdbCreditImportRequest
import dev.marcal.mediapulse.server.api.movies.MovieYearUnwatchedDto
import dev.marcal.mediapulse.server.api.movies.MovieYearWatchedDto
import dev.marcal.mediapulse.server.api.movies.MoviesByYearResponse
import dev.marcal.mediapulse.server.api.movies.MoviesByYearStatsDto
import dev.marcal.mediapulse.server.api.movies.MoviesRecentResponse
import dev.marcal.mediapulse.server.api.movies.MoviesStatsResponse
import dev.marcal.mediapulse.server.api.movies.MoviesSummaryResponse
import dev.marcal.mediapulse.server.api.movies.MoviesTotalStatsDto
import dev.marcal.mediapulse.server.api.movies.MoviesYearStatsDto
import dev.marcal.mediapulse.server.api.movies.RangeDto
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.service.movie.ExistingMovieWatchCreateFlowService
import dev.marcal.mediapulse.server.service.movie.MovieCompaniesService
import dev.marcal.mediapulse.server.service.movie.MovieCreditsService
import dev.marcal.mediapulse.server.service.movie.MovieListsService
import dev.marcal.mediapulse.server.service.movie.MovieTermsService
import dev.marcal.mediapulse.server.service.movie.MovieWatchRemovalService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoviesControllerTest {
    private val repository = mockk<MovieQueryRepository>(relaxed = true)
    private val existingMovieWatchCreateFlowService = mockk<ExistingMovieWatchCreateFlowService>(relaxed = true)
    private val movieWatchRemovalService = mockk<MovieWatchRemovalService>(relaxed = true)
    private val movieTermsService = mockk<MovieTermsService>(relaxed = true)
    private val movieCompaniesService = mockk<MovieCompaniesService>(relaxed = true)
    private val movieCreditsService = mockk<MovieCreditsService>(relaxed = true)
    private val movieListsService = mockk<MovieListsService>(relaxed = true)
    private val controller =
        MoviesController(
            repository,
            existingMovieWatchCreateFlowService,
            movieWatchRemovalService,
            movieTermsService,
            movieCompaniesService,
            movieCreditsService,
            movieListsService,
        )

    @Test
    fun `recent should delegate to repository`() {
        every { repository.recent(15, null) } returns MoviesRecentResponse(items = emptyList(), nextCursor = null)

        val result = controller.recent(15, null)

        assertEquals(0, result.items.size)
        verify(exactly = 1) { repository.recent(15, null) }
    }

    @Test
    fun `details by slug should delegate to repository`() {
        val expected =
            MovieDetailsResponse(
                movieId = 10,
                title = "De Olhos Bem Fechados",
                originalTitle = "Eyes Wide Shut",
                year = 1999,
                description = null,
                coverUrl = null,
                images = emptyList(),
                watches = emptyList(),
                externalIds = emptyList(),
            )
        every { repository.getMovieDetailsBySlug("3828") } returns expected

        val response = controller.detailsBySlug("3828")

        assertEquals(10, response.movieId)
        verify(exactly = 1) { repository.getMovieDetailsBySlug("3828") }
    }

    @Test
    fun `person details should delegate to repository`() {
        val expected =
            MoviePersonDetailsResponse(
                personId = 44,
                tmdbId = "138",
                name = "Quentin Tarantino",
                slug = "quentin-tarantino-138",
                profileUrl = null,
                roles = listOf("Direção"),
                movieCount = 4,
                watchedMoviesCount = 3,
                movies = emptyList(),
            )
        every { repository.getMoviePersonDetails("quentin-tarantino-138") } returns expected

        val response = controller.personDetails("quentin-tarantino-138")

        assertEquals(44, response.personId)
        verify(exactly = 1) { repository.getMoviePersonDetails("quentin-tarantino-138") }
    }

    @Test
    fun `company details should delegate to repository`() {
        val expected =
            MovieCompanyDetailsResponse(
                companyId = 12,
                tmdbId = "3",
                name = "Studio Ghibli",
                slug = "studio-ghibli-3",
                logoUrl = null,
                originCountry = "JP",
                companyType = MovieCompanyTypeDto.PRODUCTION,
                movieCount = 5,
                watchedMoviesCount = 4,
                movies = emptyList(),
            )
        every { repository.getMovieCompanyDetails("studio-ghibli-3") } returns expected

        val response = controller.companyDetails("studio-ghibli-3")

        assertEquals(12, response.companyId)
        verify(exactly = 1) { repository.getMovieCompanyDetails("studio-ghibli-3") }
    }

    @Test
    fun `list details should delegate to repository`() {
        val expected =
            MovieListDetailsResponse(
                listId = 9,
                name = "Favoritos",
                slug = "favoritos",
                description = "Filmes que sempre voltam.",
                movieCount = 3,
                watchedMoviesCount = 3,
                movies = emptyList(),
            )
        every { repository.getMovieListDetails("favoritos") } returns expected

        val response = controller.listDetails("favoritos")

        assertEquals(9, response.listId)
        verify(exactly = 1) { repository.getMovieListDetails("favoritos") }
    }

    @Test
    fun `lists should delegate to lists service`() {
        every { movieListsService.listAll() } returns
            listOf(
                MovieListSummaryDto(
                    listId = 3,
                    name = "Oscar 2025",
                    slug = "oscar-2025",
                    description = null,
                    itemCount = 6,
                ),
            )

        val response = controller.lists()

        assertEquals(1, response.size)
        verify(exactly = 1) { movieListsService.listAll() }
    }

    @Test
    fun `search people should delegate to credits service with capped limit`() {
        val expected =
            listOf(
                MoviePersonSuggestionDto(
                    personId = 44,
                    tmdbId = "138",
                    name = "Quentin Tarantino",
                    slug = "quentin-tarantino-138",
                    profileUrl = null,
                    roles = listOf("Direção", "Roteiro"),
                ),
            )
        every { movieCreditsService.searchPeople("quentin", 1000) } returns expected

        val response = controller.searchPeople(q = "quentin", limit = 9999)

        assertEquals(1, response.size)
        assertEquals("Quentin Tarantino", response.first().name)
        verify(exactly = 1) { movieCreditsService.searchPeople("quentin", 1000) }
    }

    @Test
    fun `delete watch should delegate to removal service`() {
        controller.deleteWatch(movieId = 58, watchId = 991)

        verify(exactly = 1) { movieWatchRemovalService.remove(movieId = 58, watchId = 991) }
    }

    @Test
    fun `term details should delegate to repository with normalized kind`() {
        val expected =
            MovieTermDetailsResponse(
                termId = 5,
                name = "Horror",
                slug = "horror",
                kind = MovieTermKindDto.GENRE,
                source = MovieTermSourceDto.TMDB,
                movieCount = 12,
                watchedMoviesCount = 8,
                movies = emptyList(),
            )
        every { repository.getMovieTermDetails("GENRE", "horror") } returns expected

        val response = controller.termDetails("genre", "horror")

        assertEquals(5, response.termId)
        verify(exactly = 1) { repository.getMovieTermDetails("GENRE", "horror") }
    }

    @Test
    fun `search terms should delegate to repository with normalized kind and capped limit`() {
        val expected =
            listOf(
                MovieTermSuggestionDto(
                    id = 9,
                    name = "Vampiros",
                    slug = "vampiros",
                    kind = MovieTermKindDto.TAG,
                    source = MovieTermSourceDto.USER,
                    hiddenGlobally = false,
                ),
            )
        every { repository.searchMovieTerms("vamp", "TAG", 1000) } returns expected

        val response = controller.searchTerms(q = "vamp", kind = "tag", limit = 5000)

        assertEquals(1, response.size)
        assertEquals("Vampiros", response.first().name)
        verify(exactly = 1) { repository.searchMovieTerms("vamp", "TAG", 1000) }
    }

    @Test
    fun `sync all terms should delegate to service with normalized limit`() {
        val expected = MovieTermsBatchSyncResponse(requestedLimit = 1000, candidates = 1000, processed = 1000, synced = 998, failed = 2)
        every { movieTermsService.syncAllFromTmdb(1000) } returns expected

        val response = controller.syncAllTermsFromTmdb(limit = 5000)

        assertEquals(998, response.synced)
        verify(exactly = 1) { movieTermsService.syncAllFromTmdb(1000) }
    }

    @Test
    fun `sync all companies should delegate to service with normalized limit`() {
        val expected = MovieCompaniesBatchSyncResponse(requestedLimit = 1000, candidates = 1000, processed = 1000, synced = 995, failed = 5)
        every { movieCompaniesService.syncAllFromTmdb(1000) } returns expected

        val response = controller.syncAllCompaniesFromTmdb(limit = 5000)

        assertEquals(995, response.synced)
        verify(exactly = 1) { movieCompaniesService.syncAllFromTmdb(1000) }
    }

    @Test
    fun `sync all credits should delegate to service with normalized limit`() {
        val expected = MovieCreditsBatchSyncResponse(requestedLimit = 1000, candidates = 1000, processed = 1000, synced = 996, failed = 4)
        every { movieCreditsService.syncAllFromTmdb(1000) } returns expected

        val response = controller.syncAllCreditsFromTmdb(limit = 5000)

        assertEquals(996, response.synced)
        verify(exactly = 1) { movieCreditsService.syncAllFromTmdb(1000) }
    }

    @Test
    fun `movie tmdb credit candidates should delegate to credits service`() {
        val expected = MovieTmdbCreditCandidatesResponse(movieId = 9, reconciledCount = 2, candidateCount = 3, groups = emptyList())
        every { movieCreditsService.fetchTmdbCandidates(9) } returns expected

        val response = controller.movieTmdbCreditCandidates(9)

        assertEquals(2, response.reconciledCount)
        verify(exactly = 1) { movieCreditsService.fetchTmdbCandidates(9) }
    }

    @Test
    fun `link existing person should delegate to credits service`() {
        val request = MoviePersonLinkRequest(personId = 44, group = "DIRECTORS", roleLabel = null)
        val expected =
            dev.marcal.mediapulse.server.api.movies.MoviePersonCreditDto(
                personId = 44,
                tmdbId = "138",
                name = "Quentin Tarantino",
                slug = "quentin-tarantino-138",
                profileUrl = null,
                creditType = dev.marcal.mediapulse.server.api.movies.MovieCreditTypeDto.CREW,
                department = "Directing",
                job = "Director",
                characterName = null,
                billingOrder = null,
            )
        every { movieCreditsService.linkExistingPerson(9, request) } returns expected

        val response = controller.linkExistingPerson(9, request)

        assertEquals(44, response.personId)
        verify(exactly = 1) { movieCreditsService.linkExistingPerson(9, request) }
    }

    @Test
    fun `import movie tmdb credit should delegate to credits service`() {
        val request =
            MovieTmdbCreditImportRequest(
                personTmdbId = "138",
                creditType = dev.marcal.mediapulse.server.api.movies.MovieCreditTypeDto.CREW,
                department = "Writing",
                job = "Writer",
            )
        val expected =
            dev.marcal.mediapulse.server.api.movies.MoviePersonCreditDto(
                personId = 44,
                tmdbId = "138",
                name = "Quentin Tarantino",
                slug = "quentin-tarantino-138",
                profileUrl = null,
                creditType = dev.marcal.mediapulse.server.api.movies.MovieCreditTypeDto.CREW,
                department = "Writing",
                job = "Writer",
                characterName = null,
                billingOrder = null,
            )
        every { movieCreditsService.importTmdbCredit(9, request) } returns expected

        val response = controller.importMovieTmdbCredit(9, request)

        assertEquals(44, response.personId)
        verify(exactly = 1) { movieCreditsService.importTmdbCredit(9, request) }
    }

    @Test
    fun `create list should delegate to lists service`() {
        val request = MovieListCreateRequest(name = "Favoritos", description = null)
        val expected =
            MovieListSummaryDto(
                listId = 1,
                name = "Favoritos",
                slug = "favoritos",
                description = null,
                itemCount = 0,
            )
        every { movieListsService.create(request) } returns expected

        val response = controller.createList(request)

        assertEquals(1, response.listId)
        verify(exactly = 1) { movieListsService.create(request) }
    }

    @Test
    fun `attach movie to list should delegate to lists service`() {
        val request = MovieListAttachRequest(listId = 1, name = null, description = null)
        val expected =
            MovieListSummaryDto(
                listId = 1,
                name = "Favoritos",
                slug = "favoritos",
                description = null,
                itemCount = 4,
            )
        every { movieListsService.attachMovie(9, request) } returns expected

        val response = controller.attachMovieToList(9, request)

        assertEquals(1, response.listId)
        verify(exactly = 1) { movieListsService.attachMovie(9, request) }
    }

    @Test
    fun `update movie list order should delegate to lists service`() {
        val request = MovieListOrderUpdateRequest(movieIds = listOf(9, 4, 7))

        controller.updateMovieListOrder(2, request)

        verify(exactly = 1) { movieListsService.updateOrder(2, request) }
    }

    @Test
    fun `summary custom without dates should fail`() {
        assertFailsWith<IllegalArgumentException> {
            controller.summary(range = "custom", start = null, end = null)
        }
    }

    @Test
    fun `summary custom should delegate with provided range`() {
        val start = Instant.parse("2026-02-01T00:00:00Z")
        val end = Instant.parse("2026-02-26T00:00:00Z")
        val expected = MoviesSummaryResponse(RangeDto(start, end), watchesCount = 5, uniqueMoviesCount = 2)

        every { repository.summary(start, end) } returns expected

        val response = controller.summary(range = "custom", start = start, end = end)

        assertEquals(5, response.watchesCount)
        assertEquals(2, response.uniqueMoviesCount)
        verify(exactly = 1) { repository.summary(start, end) }
    }

    @Test
    fun `by year should delegate with computed range and default limits`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = Instant.parse("2026-12-31T23:59:59Z")
        val expected =
            MoviesByYearResponse(
                year = 2026,
                range = RangeDto(start, end),
                stats = MoviesByYearStatsDto(watchesCount = 2, uniqueMoviesCount = 1, rewatchesCount = 1),
                watched =
                    listOf(
                        MovieYearWatchedDto(
                            movieId = 1,
                            slug = "eyes-wide-shut",
                            title = "Eyes Wide Shut",
                            originalTitle = "Eyes Wide Shut",
                            year = 1999,
                            coverUrl = "/covers/plex/movies/1/poster.jpg",
                            watchCountInYear = 2,
                            firstWatchedAt = Instant.parse("2026-01-10T21:00:00Z"),
                            lastWatchedAt = Instant.parse("2026-02-27T19:40:19Z"),
                        ),
                    ),
                unwatched =
                    listOf(
                        MovieYearUnwatchedDto(
                            movieId = 9,
                            slug = "movie-x",
                            title = "Movie X",
                            originalTitle = "Movie X",
                            year = 2001,
                            coverUrl = "/covers/plex/movies/9/poster.jpg",
                        ),
                    ),
            )
        every { repository.byYear(2026, start, end, 200, 200) } returns expected

        val response = controller.byYear(year = 2026, limitWatched = 200, limitUnwatched = 200)

        assertEquals(2026, response.year)
        assertEquals(1, response.watched.size)
        verify(exactly = 1) { repository.byYear(2026, start, end, 200, 200) }
    }

    @Test
    fun `by year should cap limits to 1000`() {
        val start = Instant.parse("2026-01-01T00:00:00Z")
        val end = Instant.parse("2026-12-31T23:59:59Z")
        every {
            repository.byYear(2026, start, end, 1000, 1000)
        } returns
            MoviesByYearResponse(
                year = 2026,
                range = RangeDto(start, end),
                stats = MoviesByYearStatsDto(watchesCount = 0, uniqueMoviesCount = 0, rewatchesCount = 0),
                watched = emptyList(),
                unwatched = emptyList(),
            )

        controller.byYear(year = 2026, limitWatched = 5000, limitUnwatched = 9999)

        verify(exactly = 1) { repository.byYear(2026, start, end, 1000, 1000) }
    }

    @Test
    fun `by year should reject invalid year`() {
        assertFailsWith<ResponseStatusException> {
            controller.byYear(year = 1899, limitWatched = 200, limitUnwatched = 200)
        }
    }

    @Test
    fun `by year should reject invalid limits`() {
        assertFailsWith<ResponseStatusException> {
            controller.byYear(year = 2026, limitWatched = 0, limitUnwatched = 200)
        }
        assertFailsWith<ResponseStatusException> {
            controller.byYear(year = 2026, limitWatched = 200, limitUnwatched = 0)
        }
    }

    @Test
    fun `stats should delegate to repository`() {
        val expected =
            MoviesStatsResponse(
                total = MoviesTotalStatsDto(watchesCount = 120, uniqueMoviesCount = 85),
                unwatchedCount = 240,
                years =
                    listOf(
                        MoviesYearStatsDto(year = 2026, watchesCount = 42, uniqueMoviesCount = 30, rewatchesCount = 12),
                        MoviesYearStatsDto(year = 2025, watchesCount = 18, uniqueMoviesCount = 16, rewatchesCount = 2),
                    ),
                latestWatchAt = Instant.parse("2026-02-27T19:40:19Z"),
                firstWatchAt = Instant.parse("2024-01-10T11:00:00Z"),
            )
        every { repository.stats() } returns expected

        val response = controller.stats()

        assertEquals(240, response.unwatchedCount)
        assertEquals(2, response.years.size)
        verify(exactly = 1) { repository.stats() }
    }
}
