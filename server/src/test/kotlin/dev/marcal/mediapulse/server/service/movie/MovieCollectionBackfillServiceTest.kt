package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.MovieCollectionCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals

class MovieCollectionBackfillServiceTest {
    private val movieCollectionCrudRepository = mockk<MovieCollectionCrudRepository>()
    private val movieRepository = mockk<MovieRepository>()
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>()
    private val service =
        MovieCollectionBackfillService(
            movieCollectionCrudRepository = movieCollectionCrudRepository,
            movieRepository = movieRepository,
            manualMovieCatalogService = manualMovieCatalogService,
        )

    @Test
    fun `vincula colecoes dos filmes existentes com tmdb`() {
        val matrix = Movie(id = 10, originalTitle = "The Matrix", year = 1999, fingerprint = "matrix")
        val dune = Movie(id = 11, originalTitle = "Dune", year = 2021, fingerprint = "dune")

        every { movieCollectionCrudRepository.findBackfillCandidates(50) } returns
            listOf(
                MovieCollectionCrudRepository.MovieCollectionBackfillCandidate(movieId = 10, tmdbId = "603"),
                MovieCollectionCrudRepository.MovieCollectionBackfillCandidate(movieId = 11, tmdbId = "438631"),
            )
        every { movieRepository.findById(10) } returns Optional.of(matrix)
        every { movieRepository.findById(11) } returns Optional.of(dune)
        every { manualMovieCatalogService.fetchTmdbMovieSnapshot("603") } returns
            ManualMovieCatalogService.TmdbMovieSnapshot(
                tmdbId = "603",
                title = "The Matrix",
                originalTitle = "The Matrix",
                imdbId = "tt0133093",
                overview = null,
                releaseYear = 1999,
                posterPath = null,
                backdropPath = null,
                posterUrl = null,
                backdropUrl = null,
                collection =
                    ManualMovieCatalogService.TmdbMovieCollectionSnapshot(
                        tmdbId = "2344",
                        name = "The Matrix Collection",
                        posterUrl = null,
                        backdropUrl = null,
                    ),
            )
        every { manualMovieCatalogService.fetchTmdbMovieSnapshot("438631") } returns
            ManualMovieCatalogService.TmdbMovieSnapshot(
                tmdbId = "438631",
                title = "Dune",
                originalTitle = "Dune",
                imdbId = "tt1160419",
                overview = null,
                releaseYear = 2021,
                posterPath = null,
                backdropPath = null,
                posterUrl = null,
                backdropUrl = null,
                collection = null,
            )
        every { movieCollectionCrudRepository.markCollectionChecked(11) } returns 1
        every { manualMovieCatalogService.assignTmdbCollection(matrix, any()) } returns matrix.copy(collectionId = 7)

        val response = service.backfill()

        assertEquals(50, response.requestedLimit)
        assertEquals(2, response.candidates)
        assertEquals(2, response.processed)
        assertEquals(1, response.linked)
        assertEquals(1, response.withoutCollection)
        assertEquals(0, response.failed)
        verify(exactly = 1) { manualMovieCatalogService.assignTmdbCollection(matrix, any()) }
        verify(exactly = 1) { movieCollectionCrudRepository.markCollectionChecked(11) }
    }

    @Test
    fun `normaliza limite e conta falhas`() {
        every { movieCollectionCrudRepository.findBackfillCandidates(500) } returns
            listOf(MovieCollectionCrudRepository.MovieCollectionBackfillCandidate(movieId = 99, tmdbId = "missing"))
        every { movieRepository.findById(99) } returns Optional.empty()

        val response = service.backfill(limit = 9999)

        assertEquals(500, response.requestedLimit)
        assertEquals(1, response.candidates)
        assertEquals(0, response.linked)
        assertEquals(0, response.withoutCollection)
        assertEquals(1, response.failed)
    }
}
