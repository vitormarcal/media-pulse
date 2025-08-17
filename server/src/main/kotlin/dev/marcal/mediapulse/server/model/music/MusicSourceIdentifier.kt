package dev.marcal.mediapulse.server.model.music

import dev.marcal.mediapulse.server.model.SourceIdentifier
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
@Table(name = "music_source_identifiers")
data class MusicSourceIdentifier(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Enumerated(EnumType.STRING)
    val externalType: SourceIdentifier,
    val externalId: String,
    @Column(name = "music_source_id", nullable = false)
    val musicSourceId: Long = 0,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
