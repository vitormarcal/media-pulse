package dev.marcal.mediapulse.server.model.music

import jakarta.persistence.Column
import jakarta.persistence.Embeddable
import jakarta.persistence.EmbeddedId
import jakarta.persistence.Entity
import jakarta.persistence.Table
import java.io.Serializable
import java.time.Instant

@Entity
@Table(name = "album_tracks")
data class AlbumTrack(
    @EmbeddedId
    val id: AlbumTrackId,
    @Column(name = "disc_number")
    val discNumber: Int? = null,
    @Column(name = "track_number")
    val trackNumber: Int? = null,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)

@Embeddable
data class AlbumTrackId(
    @Column(name = "album_id")
    val albumId: Long = 0,
    @Column(name = "track_id")
    val trackId: Long = 0,
) : Serializable
