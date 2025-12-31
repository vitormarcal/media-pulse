package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.Track
import org.springframework.data.repository.CrudRepository

interface TrackRepository : CrudRepository<Track, Long> {
    fun findByFingerprint(fingerprint: String): Track?
}
