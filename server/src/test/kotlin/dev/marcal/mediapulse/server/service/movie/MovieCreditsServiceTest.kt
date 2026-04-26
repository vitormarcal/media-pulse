package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieCreditTypeDto
import dev.marcal.mediapulse.server.api.movies.MovieDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieExternalIdDto
import dev.marcal.mediapulse.server.api.movies.MoviePersonCreditDto
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MoviePerson
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCreditsCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MoviePersonRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import kotlin.test.assertEquals

class MovieCreditsServiceTest {
    private val movieRepository = mockk<MovieRepository>()
    private val movieQueryRepository = mockk<MovieQueryRepository>()
    private val moviePersonRepository = mockk<MoviePersonRepository>(relaxed = true)
    private val movieCreditAssignmentRepository = mockk<MovieCreditAssignmentRepository>(relaxed = true)
    private val movieCreditsCrudRepository = mockk<MovieCreditsCrudRepository>()
    private val tmdbApiClient = mockk<TmdbApiClient>()
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>()
    private val transactionTemplate = mockk<TransactionTemplate>()

    private val service =
        MovieCreditsService(
            movieRepository = movieRepository,
            movieQueryRepository = movieQueryRepository,
            moviePersonRepository = moviePersonRepository,
            movieCreditAssignmentRepository = movieCreditAssignmentRepository,
            movieCreditsCrudRepository = movieCreditsCrudRepository,
            tmdbApiClient = tmdbApiClient,
            manualMovieCatalogService = manualMovieCatalogService,
            transactionTemplate = transactionTemplate,
        )

    @Test
    fun `batch sync should continue after failures`() {
        val movie1 = Movie(id = 1, originalTitle = "Movie 1", fingerprint = "fp1")
        var transactionCalls = 0
        every { movieCreditsCrudRepository.countPendingTmdbSyncCandidates() } returns 3
        every { movieCreditsCrudRepository.findTmdbSyncCandidates(3) } returns
            listOf(
                MovieCreditsCrudRepository.MovieCreditsSyncCandidate(movieId = 1, tmdbId = "101"),
                MovieCreditsCrudRepository.MovieCreditsSyncCandidate(movieId = 2, tmdbId = "202"),
                MovieCreditsCrudRepository.MovieCreditsSyncCandidate(movieId = 3, tmdbId = "303"),
            )
        every { transactionTemplate.execute<Any?>(any()) } answers {
            transactionCalls++
            if (transactionCalls == 2) {
                throw RuntimeException("boom")
            }
            val callback = arg<org.springframework.transaction.support.TransactionCallback<Any?>>(0)
            callback.doInTransaction(mockk(relaxed = true))
        }

        every { movieRepository.findById(1) } returns Optional.of(movie1)
        every { movieRepository.findById(3) } returns Optional.of(movie1.copy(id = 3, originalTitle = "Movie 3", fingerprint = "fp3"))
        every { movieCreditsCrudRepository.markCreditsSynced(any()) } returns 1

        every { movieQueryRepository.getMovieDetails(1) } returns movieDetails(1, "101")
        every { movieQueryRepository.getMovieDetails(3) } returns movieDetails(3, "303")
        every { tmdbApiClient.fetchMovieCredits("101") } returns TmdbApiClient.TmdbMovieCredits(cast = emptyList(), crew = emptyList())
        every { tmdbApiClient.fetchMovieCredits("303") } returns TmdbApiClient.TmdbMovieCredits(cast = emptyList(), crew = emptyList())
        every { movieQueryRepository.getMoviePeople(any()) } returns emptyList()

        val response = service.syncAllFromTmdb(limit = 3)

        assertEquals(3, response.candidates)
        assertEquals(3, response.processed)
        assertEquals(2, response.synced)
        assertEquals(1, response.failed)
        verify(exactly = 1) { movieCreditsCrudRepository.findTmdbSyncCandidates(3) }
    }

    @Test
    fun `sync should keep person outside cast cut when person already exists locally`() {
        val movie = Movie(id = 9, originalTitle = "Movie 9", fingerprint = "fp9")
        val capturedCredits = slot<List<MovieCreditAssignmentRepository.UpsertMovieCreditRequest>>()

        every { movieRepository.findById(9) } returns Optional.of(movie)
        every { movieQueryRepository.getMovieDetails(9) } returns movieDetails(9, "909")
        every { movieCreditsCrudRepository.markCreditsSynced(9) } returns 1
        every { movieQueryRepository.getMoviePeople(9) } returns emptyList()
        every { manualMovieCatalogService.buildTmdbImageUrl(any()) } answers { "https://image.tmdb.org/t/p/w185${firstArg<String>()}" }

        every { moviePersonRepository.findByTmdbId("2011") } returns
            MoviePerson(
                id = 2011,
                tmdbId = "2011",
                name = "Existing Actor",
                normalizedName = "existing actor",
                slug = "existing-actor-2011",
                profileUrl = null,
            )
        every { moviePersonRepository.findByTmdbId(match { it != "2011" }) } returns null
        every { moviePersonRepository.save(any()) } answers { firstArg() }
        every { movieCreditAssignmentRepository.replaceForMovie(9, capture(capturedCredits)) } returns Unit

        every { tmdbApiClient.fetchMovieCredits("909") } returns
            TmdbApiClient.TmdbMovieCredits(
                cast =
                    (0..10).map { index ->
                        TmdbApiClient.TmdbMovieCastCredit(
                            tmdbId = if (index == 10) "2011" else (1000 + index).toString(),
                            name = if (index == 10) "Existing Actor" else "Actor $index",
                            character = "Role $index",
                            order = index,
                            profilePath = null,
                        )
                    },
                crew = emptyList(),
            )

        val response = service.syncFromTmdb(9)

        assertEquals(11, response.syncedCount)
        assertEquals(11, capturedCredits.captured.size)
        assertEquals(2011L, capturedCredits.captured.last().personId)
    }

    @Test
    fun `fetch tmdb candidates should reconcile known local extras and return unresolved ones`() {
        val movie = Movie(id = 12, originalTitle = "Movie 12", fingerprint = "fp12")

        every { movieRepository.findById(12) } returns Optional.of(movie)
        every { movieQueryRepository.getMovieDetails(12) } returns movieDetails(12, "1212")
        every { manualMovieCatalogService.buildTmdbImageUrl(any()) } answers { "https://image.tmdb.org/t/p/w185${firstArg<String>()}" }
        every { movieQueryRepository.getMoviePeople(12) } returns
            listOf(
                MoviePersonCreditDto(
                    personId = 88,
                    tmdbId = "1000",
                    name = "Saved Lead",
                    slug = "saved-lead-1000",
                    profileUrl = null,
                    creditType = MovieCreditTypeDto.CAST,
                    department = null,
                    job = null,
                    characterName = "Lead",
                    billingOrder = 0,
                ),
            )

        every { moviePersonRepository.findByTmdbId("2011") } returns
            MoviePerson(
                id = 2011,
                tmdbId = "2011",
                name = "Known Extra",
                normalizedName = "known extra",
                slug = "known-extra-2011",
                profileUrl = null,
            )
        every { moviePersonRepository.findByTmdbId(match { it != "2011" }) } returns null

        every { tmdbApiClient.fetchMovieCredits("1212") } returns
            TmdbApiClient.TmdbMovieCredits(
                cast =
                    listOf(
                        TmdbApiClient.TmdbMovieCastCredit(
                            tmdbId = "1000",
                            name = "Saved Lead",
                            character = "Lead",
                            order = 0,
                            profilePath = null,
                        ),
                        TmdbApiClient.TmdbMovieCastCredit(
                            tmdbId = "2011",
                            name = "Known Extra",
                            character = "Professor",
                            order = 12,
                            profilePath = null,
                        ),
                        TmdbApiClient.TmdbMovieCastCredit(
                            tmdbId = "3012",
                            name = "Fresh Extra",
                            character = "Clerk",
                            order = 13,
                            profilePath = "/fresh.jpg",
                        ),
                    ),
                crew =
                    listOf(
                        TmdbApiClient.TmdbMovieCrewCredit(
                            tmdbId = "9001",
                            name = "Editor Name",
                            department = "Editing",
                            job = "Colorist",
                            profilePath = "/crew.jpg",
                        ),
                    ),
            )

        val response = service.fetchTmdbCandidates(12)

        assertEquals(1, response.reconciledCount)
        assertEquals(2, response.candidateCount)
        assertEquals(listOf("cast", "crew"), response.groups.map { it.id })
        assertEquals(
            "Fresh Extra",
            response.groups
                .first()
                .items
                .first()
                .name,
        )
        verify(exactly = 1) {
            movieCreditAssignmentRepository.upsert(
                withArg {
                    assertEquals(12, it.movieId)
                    assertEquals(2011L, it.personId)
                    assertEquals("Professor", it.characterName)
                },
            )
        }
    }

    private fun movieDetails(
        movieId: Long,
        tmdbId: String,
    ) = MovieDetailsResponse(
        movieId = movieId,
        title = "Movie $movieId",
        originalTitle = "Movie $movieId",
        description = null,
        year = null,
        coverUrl = null,
        images = emptyList(),
        watches = emptyList(),
        externalIds = listOf(MovieExternalIdDto(provider = "TMDB", externalId = tmdbId)),
        people = emptyList(),
        terms = emptyList(),
    )
}
