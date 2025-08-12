package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.EventSource
import org.springframework.data.jpa.repository.JpaRepository

interface EventSourceCrudRepository : JpaRepository<EventSource, Long> {
    fun findByFingerprint(fingerprint: String): EventSource?
}
