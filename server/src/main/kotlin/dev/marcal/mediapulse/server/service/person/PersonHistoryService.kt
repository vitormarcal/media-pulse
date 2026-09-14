package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.people.PersonHistoryCategory
import dev.marcal.mediapulse.server.api.people.PersonHistoryPageResponse
import dev.marcal.mediapulse.server.repository.PersonHistoryRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PersonHistoryService(
    private val repository: PersonHistoryRepository,
) {
    @Transactional(readOnly = true)
    fun mostPresent(
        category: PersonHistoryCategory,
        limit: Int,
        offset: Int,
    ): PersonHistoryPageResponse = repository.findMostPresent(category, limit.coerceIn(1, 40), offset.coerceAtLeast(0))
}
