package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.TrackMusicBrainzRecordingId
import org.springframework.data.jpa.repository.JpaRepository

interface TrackMusicBrainzRecordingIdRepository : JpaRepository<TrackMusicBrainzRecordingId, Long> {
    fun findByRecordingId(recordingId: String): TrackMusicBrainzRecordingId?
}
