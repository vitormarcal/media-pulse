package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieExternalIdDto
import dev.marcal.mediapulse.server.api.movies.MovieImageDto
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTermAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTermRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTermsCrudRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import kotlin.test.assertEquals

class MovieTermsServiceTest {
    private val movieRepository = mockk<MovieRepository>()
    private val movieQueryRepository = mockk<MovieQueryRepository>()
    private val movieTermRepository = mockk<MovieTermRepository>(relaxed = true)
    private val movieTermAssignmentRepository = mockk<MovieTermAssignmentRepository>(relaxed = true)
    private val movieTermsCrudRepository = mockk<MovieTermsCrudRepository>()
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>()
    private val transactionTemplate = mockk<TransactionTemplate>()

    private val service =
        MovieTermsService(
            movieRepository = movieRepository,
            movieQueryRepository = movieQueryRepository,
            movieTermRepository = movieTermRepository,
            movieTermAssignmentRepository = movieTermAssignmentRepository,
            movieTermsCrudRepository = movieTermsCrudRepository,
            manualMovieCatalogService = manualMovieCatalogService,
            transactionTemplate = transactionTemplate,
        )

    @Test
    fun `batch sync should continue after failures`() {
        val movie1 = Movie(id = 1, originalTitle = "Movie 1", fingerprint = "fp1")
        var transactionCalls = 0
        every { movieTermsCrudRepository.countPendingTmdbSyncCandidates() } returns 3
        every { movieTermsCrudRepository.findTmdbSyncCandidates(3) } returns
            listOf(
                MovieTermsCrudRepository.MovieTermsSyncCandidate(movieId = 1, tmdbId = "101"),
                MovieTermsCrudRepository.MovieTermsSyncCandidate(movieId = 2, tmdbId = "202"),
                MovieTermsCrudRepository.MovieTermsSyncCandidate(movieId = 3, tmdbId = "303"),
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
        every { movieTermRepository.save(any()) } answers { firstArg() }
        every { movieTermsCrudRepository.markTermsSynced(any()) } returns 1

        every { movieQueryRepository.getMovieDetails(1) } returns movieDetails(1, "101")
        every { movieQueryRepository.getMovieDetails(3) } returns movieDetails(3, "303")
        every { manualMovieCatalogService.fetchTmdbMovieSnapshot("101") } returns
            ManualMovieCatalogService.TmdbMovieSnapshot(
                tmdbId = "101",
                title = "Movie 1",
                originalTitle = "Movie 1",
                imdbId = null,
                overview = null,
                releaseYear = null,
                posterPath = null,
                backdropPath = null,
                posterUrl = null,
                backdropUrl = null,
                genres = listOf("Horror"),
                keywords = listOf("vampire"),
            )
        every { manualMovieCatalogService.fetchTmdbMovieSnapshot("303") } returns
            ManualMovieCatalogService.TmdbMovieSnapshot(
                tmdbId = "303",
                title = "Movie 3",
                originalTitle = "Movie 3",
                imdbId = null,
                overview = null,
                releaseYear = null,
                posterPath = null,
                backdropPath = null,
                posterUrl = null,
                backdropUrl = null,
                genres = listOf("Drama"),
                keywords = emptyList(),
            )
        every { movieTermRepository.findByKindAndNormalizedName(any(), any()) } returns null
        every { movieQueryRepository.getMovieTerms(any()) } returns emptyList()

        val response = service.syncAllFromTmdb(limit = 3)

        assertEquals(3, response.candidates)
        assertEquals(3, response.processed)
        assertEquals(2, response.synced)
        assertEquals(1, response.failed)
        verify(exactly = 1) { movieTermsCrudRepository.findTmdbSyncCandidates(3) }
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
        images = emptyList<MovieImageDto>(),
        watches = emptyList(),
        externalIds = listOf(MovieExternalIdDto(provider = "TMDB", externalId = tmdbId)),
        terms = emptyList(),
    )
}
