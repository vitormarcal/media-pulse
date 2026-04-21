package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.repository.crud.MovieWatchCrudRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class MovieWatchRemovalService(
    private val movieWatchCrudRepository: MovieWatchCrudRepository,
) {
    fun remove(
        movieId: Long,
        watchId: Long,
    ) {
        val watch =
            movieWatchCrudRepository.findById(watchId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "sessão não encontrada")
            }

        if (watch.movieId != movieId) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "sessão não encontrada para este filme")
        }

        movieWatchCrudRepository.delete(watch)
    }
}
