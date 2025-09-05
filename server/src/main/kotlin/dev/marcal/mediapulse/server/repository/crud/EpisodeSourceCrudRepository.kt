package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.series.EpisodeSource
import org.springframework.data.jpa.repository.JpaRepository

interface EpisodeSourceCrudRepository : JpaRepository<EpisodeSource, Long> {
    fun findByFingerprint(fingerprint: String): EpisodeSource?
}
