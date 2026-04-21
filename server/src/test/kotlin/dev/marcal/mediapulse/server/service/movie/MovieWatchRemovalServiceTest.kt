package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.model.movie.MovieWatch
import dev.marcal.mediapulse.server.model.movie.MovieWatchSource
import dev.marcal.mediapulse.server.repository.crud.MovieWatchCrudRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.Optional
import kotlin.test.assertFailsWith

class MovieWatchRemovalServiceTest {
    private val movieWatchCrudRepository = mockk<MovieWatchCrudRepository>(relaxed = true)
    private val service = MovieWatchRemovalService(movieWatchCrudRepository)

    @Test
    fun `remove sessao quando pertence ao filme`() {
        val watch =
            MovieWatch(
                id = 991,
                movieId = 58,
                source = MovieWatchSource.MANUAL,
                watchedAt = Instant.parse("2026-04-14T22:00:00Z"),
            )

        every { movieWatchCrudRepository.findById(991) } returns Optional.of(watch)

        service.remove(movieId = 58, watchId = 991)

        verify(exactly = 1) { movieWatchCrudRepository.delete(watch) }
    }

    @Test
    fun `rejeita sessao de outro filme`() {
        val watch =
            MovieWatch(
                id = 991,
                movieId = 99,
                source = MovieWatchSource.MANUAL,
                watchedAt = Instant.parse("2026-04-14T22:00:00Z"),
            )

        every { movieWatchCrudRepository.findById(991) } returns Optional.of(watch)

        assertFailsWith<ResponseStatusException> {
            service.remove(movieId = 58, watchId = 991)
        }

        verify(exactly = 0) { movieWatchCrudRepository.delete(any<MovieWatch>()) }
    }
}
