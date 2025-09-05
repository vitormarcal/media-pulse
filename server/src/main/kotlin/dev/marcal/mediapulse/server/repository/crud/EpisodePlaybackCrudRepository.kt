package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.series.EpisodePlayback
import org.springframework.data.jpa.repository.JpaRepository

interface EpisodePlaybackCrudRepository : JpaRepository<EpisodePlayback, Long>
