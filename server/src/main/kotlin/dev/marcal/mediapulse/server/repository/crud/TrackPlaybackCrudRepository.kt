package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.TrackPlayback
import org.springframework.data.jpa.repository.JpaRepository

interface TrackPlaybackCrudRepository : JpaRepository<TrackPlayback, Long>
