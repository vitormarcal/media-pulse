package dev.marcal.mediapulse.server.controller.people

import dev.marcal.mediapulse.server.api.movies.PersonDetailsResponse
import dev.marcal.mediapulse.server.api.movies.PersonFilmographyResponse
import dev.marcal.mediapulse.server.api.movies.PersonShowFilmographyResponse
import dev.marcal.mediapulse.server.api.movies.PersonSuggestionDto
import dev.marcal.mediapulse.server.service.movie.MovieCreditsService
import dev.marcal.mediapulse.server.service.person.PersonDetailsService
import dev.marcal.mediapulse.server.service.person.PersonFilmographyService
import dev.marcal.mediapulse.server.service.person.PersonShowFilmographyService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PeopleControllerTest {
    private val personDetailsService = mockk<PersonDetailsService>()
    private val movieCreditsService = mockk<MovieCreditsService>()
    private val personFilmographyService = mockk<PersonFilmographyService>()
    private val personShowFilmographyService = mockk<PersonShowFilmographyService>()
    private val controller =
        PeopleController(
            personDetailsService = personDetailsService,
            movieCreditsService = movieCreditsService,
            personFilmographyService = personFilmographyService,
            personShowFilmographyService = personShowFilmographyService,
        )

    @Test
    fun `details should delegate to repository`() {
        val expected =
            PersonDetailsResponse(
                personId = 44,
                tmdbId = "138",
                name = "Quentin Tarantino",
                slug = "quentin-tarantino-138",
                profileUrl = null,
                roles = listOf("Direção"),
                movieCount = 4,
                watchedMoviesCount = 3,
                movies = emptyList(),
                tmdbProfile = null,
            )
        every { personDetailsService.fetchDetails("quentin-tarantino-138") } returns expected

        val response = controller.details("quentin-tarantino-138")

        assertEquals(44, response.personId)
        verify(exactly = 1) { personDetailsService.fetchDetails("quentin-tarantino-138") }
    }

    @Test
    fun `search should delegate to credits service`() {
        val expected =
            listOf(
                PersonSuggestionDto(
                    personId = 44,
                    tmdbId = "138",
                    name = "Quentin Tarantino",
                    slug = "quentin-tarantino-138",
                    profileUrl = null,
                    roles = listOf("Direção", "Roteiro"),
                ),
            )
        every { movieCreditsService.searchPeople("quentin", 1000) } returns expected

        val response = controller.search(q = "quentin", limit = 9999)

        assertEquals(1, response.size)
        assertEquals("Quentin Tarantino", response.first().name)
        verify(exactly = 1) { movieCreditsService.searchPeople("quentin", 1000) }
    }

    @Test
    fun `tmdb filmography should delegate to service`() {
        val expected =
            PersonFilmographyResponse(
                personId = 44,
                tmdbId = "138",
                name = "Quentin Tarantino",
                profileUrl = null,
                members = emptyList(),
            )
        every { personFilmographyService.fetchFilmography(44) } returns expected

        val response = controller.tmdbFilmography(44)

        assertEquals("138", response.tmdbId)
        verify(exactly = 1) { personFilmographyService.fetchFilmography(44) }
    }

    @Test
    fun `tmdb show filmography should delegate to service`() {
        val expected =
            PersonShowFilmographyResponse(
                personId = 44,
                tmdbId = "138",
                name = "Quentin Tarantino",
                profileUrl = null,
                members = emptyList(),
            )
        every { personShowFilmographyService.fetchFilmography(44) } returns expected

        val response = controller.tmdbShowFilmography(44)

        assertEquals("138", response.tmdbId)
        verify(exactly = 1) { personShowFilmographyService.fetchFilmography(44) }
    }
}
