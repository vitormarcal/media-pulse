package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.repository.ShowPersonalMarksRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ShowPersonalMarksService(
    private val repository: ShowPersonalMarksRepository,
) {
    @Transactional
    fun setFavorite(
        id: Long,
        enabled: Boolean,
    ) {
        if (!repository.setFavorite(id, enabled)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")
    }

    @Transactional
    fun setAbandoned(
        id: Long,
        enabled: Boolean,
    ) {
        if (!repository.setAbandoned(id, enabled)) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")
    }
}
