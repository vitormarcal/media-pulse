package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.MovieCardDto
import dev.marcal.mediapulse.server.api.movies.MoviesSummaryResponse
import dev.marcal.mediapulse.server.api.movies.RangeDto
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MoviesControllerTest {
    private val repository = mockk<MovieQueryRepository>(relaxed = true)
    private val controller = MoviesController(repository)

    @Test
    fun `recent should delegate to repository`() {
        every { repository.recent(15) } returns emptyList()

        val result: List<MovieCardDto> = controller.recent(15)

        assertEquals(0, result.size)
        verify(exactly = 1) { repository.recent(15) }
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
}
