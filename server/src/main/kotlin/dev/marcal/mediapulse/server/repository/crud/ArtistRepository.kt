package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.Artist
import org.springframework.data.repository.CrudRepository

interface ArtistRepository : CrudRepository<Artist, Long> {
    fun findByFingerprint(fingerprint: String): Artist?
}
