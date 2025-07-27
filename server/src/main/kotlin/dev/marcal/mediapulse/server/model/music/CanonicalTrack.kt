package dev.marcal.mediapulse.server.model.music

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint

@Entity
@Table(
    name = "canonical_tracks",
    uniqueConstraints = [UniqueConstraint(columnNames = ["canonical_id", "canonical_type"])],
)
data class CanonicalTrack(
    @Id @GeneratedValue
    val id: Long = 0,
    @Column(name = "canonical_id", nullable = false)
    val canonicalId: String,
    @Column(name = "canonical_type", nullable = false)
    val canonicalType: String,
    val title: String,
    val album: String,
    val artist: String,
)
