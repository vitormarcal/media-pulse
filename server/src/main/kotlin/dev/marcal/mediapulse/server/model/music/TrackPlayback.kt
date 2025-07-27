package dev.marcal.mediapulse.server.model.music

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "track_playbacks")
data class TrackPlayback(
    @Id @GeneratedValue
    val id: Long = 0,
    @Column(name = "canonical_track_id", nullable = false)
    val canonicalTrackId: Long,
    @Column(name = "source_event_id")
    val sourceEventId: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(name = "playback_source", nullable = false)
    val source: PlaybackSource,
    @Column(name = "played_at", nullable = false)
    val playedAt: Instant = Instant.now(),
)
