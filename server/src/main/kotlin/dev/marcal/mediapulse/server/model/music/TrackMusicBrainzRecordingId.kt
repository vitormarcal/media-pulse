package dev.marcal.mediapulse.server.model.music

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "track_musicbrainz_recording_ids")
data class TrackMusicBrainzRecordingId(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "track_id", nullable = false)
    val trackId: Long,
    @Column(name = "recording_id", nullable = false, unique = true)
    val recordingId: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
