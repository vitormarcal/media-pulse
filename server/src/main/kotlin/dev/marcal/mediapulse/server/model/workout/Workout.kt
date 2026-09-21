package dev.marcal.mediapulse.server.model.workout

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.math.BigDecimal
import java.time.Instant

enum class WorkoutCategory { RUNNING, JUMP_ROPE, GYM }

@Entity
@Table(name = "workouts")
data class Workout(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    val category: WorkoutCategory,
    @Column(name = "started_at", nullable = false)
    val startedAt: Instant,
    @Column(name = "duration_minutes", nullable = false)
    val durationMinutes: Int,
    @Column(name = "distance_km", precision = 10, scale = 3)
    val distanceKm: BigDecimal? = null,
    val jumps: Int? = null,
    @Column(length = 200)
    val location: String? = null,
    @Column(name = "photo_url", columnDefinition = "TEXT")
    val photoUrl: String? = null,
    @Column(name = "created_at", nullable = false)
    val createdAt: Instant = Instant.now(),
)
