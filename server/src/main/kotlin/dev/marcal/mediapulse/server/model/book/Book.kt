package dev.marcal.mediapulse.server.model.book

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Entity
@Table(
    name = "books",
    uniqueConstraints = [
        UniqueConstraint(columnNames = ["fingerprint"]),
    ],
)
data class Book(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val title: String,
    @Column(name = "release_date")
    val releaseDate: LocalDate? = null,
    val description: String? = null,
    @Column(name = "cover_url")
    val coverUrl: String? = null,
    val rating: BigDecimal? = null,
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(name = "review_raw", columnDefinition = "TEXT")
    val reviewRaw: String? = null,
    @Column(name = "reviewed_at")
    val reviewedAt: Instant? = null,
    @Column(nullable = false, unique = true)
    val fingerprint: String,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
)
