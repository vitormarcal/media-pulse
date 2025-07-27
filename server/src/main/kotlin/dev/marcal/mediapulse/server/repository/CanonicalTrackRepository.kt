package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.model.music.CanonicalTrack
import org.springframework.data.jpa.repository.JpaRepository

interface CanonicalTrackRepository : JpaRepository<CanonicalTrack, Long> {
    fun findByCanonicalIdAndCanonicalType(
        canonicalId: String,
        canonicalType: String,
    ): CanonicalTrack?
}
