package dev.marcal.mediapulse.server.model.music

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "track_playbacks")
data class TrackPlayback(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "track_id", nullable = false)
    val trackId: Long,
    @Column(name = "source_event_id")
    val sourceEventId: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "playback_source", nullable = false)
    val source: PlaybackSource,
    @Column(name = "played_at", nullable = false)
    val playedAt: Instant = Instant.now(),
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
