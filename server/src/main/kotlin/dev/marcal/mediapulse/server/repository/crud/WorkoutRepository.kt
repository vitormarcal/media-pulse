package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.workout.Workout
import dev.marcal.mediapulse.server.model.workout.WorkoutCategory
import org.springframework.data.domain.Pageable
import org.springframework.data.domain.Slice
import org.springframework.data.jpa.repository.JpaRepository

interface WorkoutRepository : JpaRepository<Workout, Long> {
    fun findAllBy(pageable: Pageable): Slice<Workout>

    fun findAllByCategory(
        category: WorkoutCategory,
        pageable: Pageable,
    ): Slice<Workout>
}
