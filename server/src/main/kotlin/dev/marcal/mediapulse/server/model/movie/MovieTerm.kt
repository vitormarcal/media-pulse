package dev.marcal.mediapulse.server.model.movie

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
    name = "movie_terms",
    uniqueConstraints = [UniqueConstraint(columnNames = ["kind", "normalized_name"])],
)
data class MovieTerm(
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
    val kind: MovieTermKind,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val source: MovieTermSource,
    @Column(nullable = false)
    val hidden: Boolean = false,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
)
