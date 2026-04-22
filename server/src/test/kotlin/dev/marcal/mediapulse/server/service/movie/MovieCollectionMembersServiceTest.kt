package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.crud.MovieCollectionCrudRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovieCollectionMembersServiceTest {
    private val movieCollectionCrudRepository = mockk<MovieCollectionCrudRepository>()
    private val tmdbApiClient = mockk<TmdbApiClient>()
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>()
    private val service =
        MovieCollectionMembersService(
            movieCollectionCrudRepository = movieCollectionCrudRepository,
            tmdbApiClient = tmdbApiClient,
            manualMovieCatalogService = manualMovieCatalogService,
        )

    @Test
    fun `combina membros tmdb com filmes locais da colecao`() {
        every { movieCollectionCrudRepository.findCollection(12) } returns
            MovieCollectionCrudRepository.MovieCollectionRecord(
                id = 12,
                tmdbId = "2344",
                name = "The Matrix Collection",
                posterUrl = "/covers/tmdb/collections/matrix.jpg",
                backdropUrl = null,
            )
        every { tmdbApiClient.fetchMovieCollectionDetails("2344") } returns
            TmdbApiClient.TmdbMovieCollectionDetails(
                tmdbId = "2344",
                name = "The Matrix Collection",
                overview = "Matrix films.",
                posterPath = "/poster.jpg",
                backdropPath = "/backdrop.jpg",
                parts =
                    listOf(
                        TmdbApiClient.TmdbMovieCollectionPart(
                            tmdbId = "603",
                            title = "The Matrix",
                            originalTitle = "The Matrix",
                            overview = "Reality bends.",
                            releaseYear = 1999,
                            posterPath = "/matrix.jpg",
                            backdropPath = null,
                        ),
                        TmdbApiClient.TmdbMovieCollectionPart(
                            tmdbId = "604",
                            title = "The Matrix Reloaded",
                            originalTitle = "The Matrix Reloaded",
                            overview = "The story continues.",
                            releaseYear = 2003,
                            posterPath = "/reloaded.jpg",
                            backdropPath = null,
                        ),
                    ),
            )
        every { movieCollectionCrudRepository.findLocalMoviesByTmdbIds(listOf("603", "604")) } returns
            mapOf(
                "603" to
                    MovieCollectionCrudRepository.LocalMovieByTmdbId(
                        tmdbId = "603",
                        movieId = 10,
                        slug = "the-matrix",
                    ),
            )
        every { manualMovieCatalogService.buildTmdbImageUrl(any()) } answers {
            "https://image.tmdb.org/t/p/w780${firstArg<String>()}"
        }

        val response = service.fetchMembers(12)

        assertEquals("The Matrix Collection", response.name)
        assertEquals("https://image.tmdb.org/t/p/w780/poster.jpg", response.posterUrl)
        assertEquals(2, response.members.size)
        assertTrue(response.members[0].inCatalog)
        assertEquals(10L, response.members[0].localMovieId)
        assertEquals("the-matrix", response.members[0].localSlug)
        assertFalse(response.members[1].inCatalog)
        assertEquals("https://www.themoviedb.org/movie/604", response.members[1].tmdbUrl)
    }
}
