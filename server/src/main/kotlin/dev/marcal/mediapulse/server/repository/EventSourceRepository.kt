package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.model.EventSource
import org.springframework.data.jpa.repository.JpaRepository

interface EventSourceRepository : JpaRepository<EventSource, Long> {
    fun findByFingerprint(fingerprint: String): EventSource?
}
