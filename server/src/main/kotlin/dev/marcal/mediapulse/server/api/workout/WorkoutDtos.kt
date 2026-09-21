package dev.marcal.mediapulse.server.api.workout

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dev.marcal.mediapulse.server.model.workout.WorkoutCategory
import java.math.BigDecimal
import java.time.Instant

data class WorkoutCreateRequest(
    val category: WorkoutCategory,
    val startedAt: Instant,
    @JsonDeserialize(using = WorkoutIntegerDeserializer::class)
    val durationMinutes: Int,
    val distanceKm: BigDecimal? = null,
    @JsonDeserialize(using = WorkoutIntegerDeserializer::class)
    val jumps: Int? = null,
    val location: String? = null,
)

data class WorkoutDto(
    val id: Long,
    val category: WorkoutCategory,
    val startedAt: Instant,
    val durationMinutes: Int,
    val distanceKm: BigDecimal?,
    val jumps: Int?,
    val location: String?,
    val photoUrl: String?,
)

data class WorkoutHistoryResponse(
    val items: List<WorkoutDto>,
    val nextPage: Int?,
)
