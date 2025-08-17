package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.EventSource
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface EventSourceCrudRepository : JpaRepository<EventSource, Long> {
    fun findByFingerprint(fingerprint: String): EventSource?

    fun countByStatusIn(status: List<EventSource.Status>): Long

    fun findAllByStatusIn(
        status: List<EventSource.Status>,
        pageable: Pageable,
    ): Page<EventSource>
}
