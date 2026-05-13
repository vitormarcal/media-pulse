package dev.marcal.mediapulse.server.model.music

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(
    name = "album_terms",
    uniqueConstraints = [UniqueConstraint(columnNames = ["kind", "normalized_name"])],
)
data class AlbumTerm(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val name: String,
    @Column(name = "normalized_name", nullable = false)
    val normalizedName: String,
    @Column(nullable = false)
    val slug: String,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val kind: AlbumTermKind,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val source: AlbumTermSource,
    @Column(nullable = false)
    val hidden: Boolean = false,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
)
