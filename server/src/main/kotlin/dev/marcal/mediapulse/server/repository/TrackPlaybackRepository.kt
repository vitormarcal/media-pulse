package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.model.music.TrackPlayback
import org.springframework.data.jpa.repository.JpaRepository

interface TrackPlaybackRepository : JpaRepository<TrackPlayback, Long>
