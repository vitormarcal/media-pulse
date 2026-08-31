package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.movies.PersonDetailsResponse
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PersonDetailsServiceTest {
    private val repository = mockk<MovieQueryRepository>()
    private val service = PersonDetailsService(repository)

    @Test
    fun `fetch local details should return repository snapshot`() {
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
    }
}
