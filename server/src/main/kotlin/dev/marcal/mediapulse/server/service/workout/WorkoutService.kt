package dev.marcal.mediapulse.server.service.workout

import dev.marcal.mediapulse.server.api.workout.WorkoutCreateRequest
import dev.marcal.mediapulse.server.api.workout.WorkoutDto
import dev.marcal.mediapulse.server.api.workout.WorkoutHistoryResponse
import dev.marcal.mediapulse.server.model.workout.Workout
import dev.marcal.mediapulse.server.model.workout.WorkoutCategory
import dev.marcal.mediapulse.server.repository.crud.WorkoutRepository
import dev.marcal.mediapulse.server.util.TxUtil
import org.springframework.data.domain.PageRequest
import org.springframework.data.domain.Sort
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal

@Service
class WorkoutService(
    private val repository: WorkoutRepository,
    private val photos: WorkoutPhotoService,
    private val tx: TxUtil,
) {
    fun create(
        request: WorkoutCreateRequest,
        photo: MultipartFile?,
    ): WorkoutDto {
        validate(request)
        val photoUrl = photo?.let(photos::save)
        try {
            return tx.inTx {
                repository
                    .saveAndFlush(
                        Workout(
                            category = request.category,
                            startedAt = request.startedAt,
                            durationMinutes = request.durationMinutes,
                            distanceKm = request.distanceKm,
                            jumps = request.jumps,
                            location = request.location?.trim()?.ifBlank { null },
                            photoUrl = photoUrl,
                        ),
                    ).toDto()
            }
        } catch (error: Exception) {
            if (photoUrl != null) {
                runCatching { photos.delete(photoUrl) }.exceptionOrNull()?.let(error::addSuppressed)
            }
            throw error
        }
    }

    fun history(
        category: WorkoutCategory?,
        page: Int,
        limit: Int,
    ): WorkoutHistoryResponse {
        if (page < 0 || limit !in 1..100) badRequest("Paginação inválida.")
        val pageable = PageRequest.of(page, limit, Sort.by(Sort.Direction.DESC, "startedAt", "id"))
        val result = if (category == null) repository.findAllBy(pageable) else repository.findAllByCategory(category, pageable)
        return WorkoutHistoryResponse(result.content.map { it.toDto() }, if (result.hasNext()) page + 1 else null)
    }

    private fun validate(request: WorkoutCreateRequest) {
        if (request.durationMinutes <= 0) badRequest("Informe uma duração positiva em minutos.")
        val distance = request.distanceKm
        if (request.category == WorkoutCategory.RUNNING) {
            if (distance == null ||
                distance <= BigDecimal.ZERO ||
                distance >= BigDecimal("10000000") ||
                distance.stripTrailingZeros().scale() > 3
            ) {
                badRequest("Informe a distância em km, positiva e com até três casas decimais.")
            }
        } else if (distance != null) {
            badRequest("Distância se aplica apenas à corrida.")
        }
        if (request.jumps != null && (request.category != WorkoutCategory.JUMP_ROPE || request.jumps <= 0)) {
            badRequest("Informe uma quantidade positiva de pulos apenas para corda.")
        }
        if ((request.location?.trim()?.length ?: 0) > 200) badRequest("O local deve ter até 200 caracteres.")
    }

    private fun badRequest(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private fun Workout.toDto() = WorkoutDto(id, category, startedAt, durationMinutes, distanceKm, jumps, location, photoUrl)
}
