package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.MusicSource
import org.springframework.data.jpa.repository.JpaRepository

interface MusicSourceCrudRepository : JpaRepository<MusicSource, Long> {
    fun findByFingerprint(fingerprint: String): MusicSource?
}
