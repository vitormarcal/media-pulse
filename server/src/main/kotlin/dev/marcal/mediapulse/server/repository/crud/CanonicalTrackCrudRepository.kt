package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.CanonicalTrack
import org.springframework.data.jpa.repository.JpaRepository

interface CanonicalTrackCrudRepository : JpaRepository<CanonicalTrack, Long> {
    fun findByCanonicalIdAndCanonicalType(
        canonicalId: String,
        canonicalType: String,
    ): CanonicalTrack?
}
