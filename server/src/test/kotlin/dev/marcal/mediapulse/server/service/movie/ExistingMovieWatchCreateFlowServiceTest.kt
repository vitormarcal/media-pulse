package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ExistingMovieWatchCreateFlowServiceTest {
    private val movieRepository = mockk<MovieRepository>()
    private val manualMovieWatchRegistrationService = mockk<ManualMovieWatchRegistrationService>()

    private val service =
        ExistingMovieWatchCreateFlowService(
            movieRepository = movieRepository,
            manualMovieWatchRegistrationService = manualMovieWatchRegistrationService,
        )

    @Test
    fun `registra sessao manual para filme existente`() {
        val watchedAt = Instant.parse("2026-04-14T22:00:00Z")
        val movie =
            Movie(
                id = 58,
                originalTitle = "Le Mépris",
                year = 1963,
                coverUrl = "/cover.jpg",
                tmdbId = "115",
                fingerprint = "fp",
            )

        every { movieRepository.findById(58) } returns Optional.of(movie)
        every { manualMovieWatchRegistrationService.register(58, watchedAt) } returns true
        val response = service.execute(58, watchedAt)

        assertEquals(58, response.movieId)
        assertEquals("Le Mépris", response.title)
        assertEquals("MANUAL", response.source)
        assertTrue(response.watchInserted)
        assertFalse(response.createdMovie)
    }
}
