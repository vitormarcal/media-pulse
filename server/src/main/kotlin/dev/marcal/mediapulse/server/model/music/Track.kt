package dev.marcal.mediapulse.server.model.music

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "tracks",
    uniqueConstraints = [UniqueConstraint(columnNames = ["fingerprint"])],
)
data class Track(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "album_id", nullable = false) val albumId: Long,
    @Column(nullable = false) val title: String,
    @Column(name = "track_number") val trackNumber: Int? = null,
    @Column(name = "disc_number") val discNumber: Int? = null,
    @Column(name = "duration_ms") val durationMs: Int? = null,
    @Column(nullable = false, unique = true) val fingerprint: String,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") val updatedAt: Instant? = null,
)
