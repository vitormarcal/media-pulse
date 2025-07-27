package dev.marcal.mediapulse.server.model.music

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table

@Entity
@Table(
    name = "canonical_tracks",
)
data class CanonicalTrack(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "canonical_id", nullable = false)
    val canonicalId: String,
    @Column(name = "canonical_type", nullable = false)
    val canonicalType: String,
    val title: String,
    val album: String,
    val artist: String,
)
