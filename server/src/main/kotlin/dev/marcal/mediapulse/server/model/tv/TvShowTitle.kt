package dev.marcal.mediapulse.server.model.tv

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
@Table(name = "tv_show_titles")
data class TvShowTitle(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "show_id", nullable = false)
    val showId: Long,
    @Column(nullable = false)
    val title: String,
    @Column(nullable = true)
    val locale: String? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val source: TvShowTitleSource,
    @Column(name = "is_primary", nullable = false)
    val isPrimary: Boolean = false,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
