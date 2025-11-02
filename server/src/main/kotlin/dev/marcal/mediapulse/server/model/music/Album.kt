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
    name = "albums",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["fingerprint"]),
        UniqueConstraint(columnNames = ["artist_id", "title", "year"]),
    ],
)
data class Album(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "artist_id", nullable = false) val artistId: Long,
    @Column(nullable = false) val title: String,
    val year: Int? = null,
    @Column(name = "cover_url") val coverUrl: String? = null,
    @Column(nullable = false, unique = true) val fingerprint: String,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") val updatedAt: Instant? = null,
)
