package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.movies.PersonDetailsResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.service.movie.ManualMovieCatalogService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PersonDetailsServiceTest {
    private val repository = mockk<MovieQueryRepository>()
    private val tmdbApiClient = mockk<TmdbApiClient>()
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>()

    private val service =
        PersonDetailsService(
            repository = repository,
            tmdbApiClient = tmdbApiClient,
            manualMovieCatalogService = manualMovieCatalogService,
        )

    @Test
    fun `fetch local details should not call tmdb`() {
        val local =
            PersonDetailsResponse(
                personId = 44,
                tmdbId = "138",
                name = "Quentin Tarantino",
                slug = "quentin-tarantino-138",
                profileUrl = null,
                roles = listOf("Direção", "Roteiro"),
                movieCount = 4,
                watchedMoviesCount = 3,
                movies = emptyList(),
            )
        every { repository.getPersonDetails("quentin-tarantino-138") } returns local

        val response = service.fetchLocalDetails("quentin-tarantino-138")

        assertEquals(local, response)
        verify(exactly = 0) { tmdbApiClient.fetchPersonDetails(any()) }
    }

    @Test
    fun `fetch tmdb profile should return external profile separately`() {
        every { repository.getPersonDetails("quentin-tarantino-138") } returns
            PersonDetailsResponse(
                personId = 44,
                tmdbId = "138",
                name = "Quentin Tarantino",
                slug = "quentin-tarantino-138",
                profileUrl = null,
                roles = listOf("Direção", "Roteiro"),
                movieCount = 4,
                watchedMoviesCount = 3,
                movies = emptyList(),
            )
        every { tmdbApiClient.fetchPersonDetails("138") } returns
            TmdbApiClient.TmdbPersonDetails(
                tmdbId = "138",
                name = "Quentin Tarantino",
                biography = "Biografia curta.",
                birthday = "1963-03-27",
                deathday = null,
                placeOfBirth = "Knoxville, Tennessee, USA",
                knownForDepartment = "Directing",
                alsoKnownAs = listOf("QT"),
                homepage = "https://example.com",
                imdbId = "nm0000233",
                popularity = 18.4,
                profilePath = "/qt.jpg",
            )
        every { manualMovieCatalogService.buildTmdbImageUrl("/qt.jpg") } returns "https://image.tmdb.org/t/p/original/qt.jpg"

        val response = service.fetchTmdbProfile("quentin-tarantino-138")

        assertEquals("Biografia curta.", response?.biography)
        assertEquals("Directing", response?.knownForDepartment)
        assertEquals("https://image.tmdb.org/t/p/original/qt.jpg", response?.profileUrl)
        assertEquals(listOf("QT"), response?.aliases)
    }
}
