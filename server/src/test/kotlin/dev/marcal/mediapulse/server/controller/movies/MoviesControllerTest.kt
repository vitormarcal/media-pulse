package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.MovieCardDto
import dev.marcal.mediapulse.server.api.movies.MovieDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieYearUnwatchedDto
import dev.marcal.mediapulse.server.api.movies.MovieYearWatchedDto
import dev.marcal.mediapulse.server.api.movies.MoviesByYearResponse
import dev.marcal.mediapulse.server.api.movies.MoviesByYearStatsDto
import dev.marcal.mediapulse.server.api.movies.MoviesSummaryResponse
import dev.marcal.mediapulse.server.api.movies.RangeDto
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
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
    private val controller = MoviesController(repository)

    @Test
    fun `recent should delegate to repository`() {
        every { repository.recent(15) } returns emptyList()

        val result: List<MovieCardDto> = controller.recent(15)

        assertEquals(0, result.size)
        verify(exactly = 1) { repository.recent(15) }
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
}
