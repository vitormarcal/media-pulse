package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.repository.MoviePersonalMarksRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class MoviePersonalMarksService(
    private val repository: MoviePersonalMarksRepository,
) {
    @Transactional
    fun setFavorite(
        id: Long,
        enabled: Boolean,
    ) {
        if (!repository.setFavorite(id, enabled)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found")
    }

    @Transactional
    fun setAbandoned(
        id: Long,
        enabled: Boolean,
    ) {
        if (!repository.setAbandoned(id, enabled)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found")
    }
}
