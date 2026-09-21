package dev.marcal.mediapulse.server.controller.workout

import dev.marcal.mediapulse.server.api.workout.WorkoutCreateRequest
import dev.marcal.mediapulse.server.api.workout.WorkoutDto
import dev.marcal.mediapulse.server.api.workout.WorkoutHistoryResponse
import dev.marcal.mediapulse.server.model.workout.WorkoutCategory
import dev.marcal.mediapulse.server.service.workout.WorkoutService
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/workouts")
class WorkoutsController(
    private val service: WorkoutService,
) {
    @ExceptionHandler(ResponseStatusException::class)
    fun invalidWorkout(error: ResponseStatusException): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(error.statusCode).body(
            ProblemDetail.forStatusAndDetail(
                error.statusCode,
                error.reason ?: "Confira os dados do treino.",
            ),
        )

    @GetMapping
    fun history(
        @RequestParam(required = false) category: WorkoutCategory?,
        @RequestParam(defaultValue = "0") page: Int,
        @RequestParam(defaultValue = "24") limit: Int,
    ): WorkoutHistoryResponse = service.history(category, page, limit)

    @PostMapping(consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    @ResponseStatus(HttpStatus.CREATED)
    fun create(
        @RequestPart("workout") request: WorkoutCreateRequest,
        @RequestPart("photo", required = false) photo: MultipartFile?,
    ): WorkoutDto = service.create(request, photo)
}
