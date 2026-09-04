package dev.marcal.mediapulse.server.model.tv

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant

@Entity
@Table(name = "show_lists")
data class ShowList(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false)
    val name: String,
    @Column(name = "normalized_name", nullable = false)
    val normalizedName: String,
    @Column(nullable = false, unique = true)
    val slug: String,
    @Column(columnDefinition = "TEXT")
    val description: String? = null,
    @Column(name = "cover_show_id")
    val coverShowId: Long? = null,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
)
