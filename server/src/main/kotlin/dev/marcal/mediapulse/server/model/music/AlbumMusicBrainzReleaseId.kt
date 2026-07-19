package dev.marcal.mediapulse.server.model.music

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "album_musicbrainz_release_ids")
data class AlbumMusicBrainzReleaseId(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "album_id", nullable = false)
    val albumId: Long,
    @Column(name = "release_id", nullable = false, unique = true)
    val releaseId: String,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
