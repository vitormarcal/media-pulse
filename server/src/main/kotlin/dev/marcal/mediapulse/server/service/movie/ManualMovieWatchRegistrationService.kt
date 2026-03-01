package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.model.movie.MovieWatchSource
import dev.marcal.mediapulse.server.repository.crud.MovieWatchCrudRepository
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class ManualMovieWatchRegistrationService(
    private val movieWatchCrudRepository: MovieWatchCrudRepository,
) {
    fun register(
        movieId: Long,
        watchedAt: Instant,
    ): Boolean {
        val alreadyExists =
            movieWatchCrudRepository.existsByMovieIdAndSourceAndWatchedAt(
                movieId = movieId,
                source = MovieWatchSource.MANUAL,
                watchedAt = watchedAt,
            )

        movieWatchCrudRepository.insertIgnore(
            movieId = movieId,
            source = MovieWatchSource.MANUAL.name,
            watchedAt = watchedAt,
        )

        return !alreadyExists
    }
}
