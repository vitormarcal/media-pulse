package dev.marcal.mediapulse.server.controller.workout

import dev.marcal.mediapulse.server.api.workout.WorkoutDto
import dev.marcal.mediapulse.server.api.workout.WorkoutHistoryResponse
import dev.marcal.mediapulse.server.model.workout.WorkoutCategory
import dev.marcal.mediapulse.server.service.workout.WorkoutService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders
import java.time.Instant

class WorkoutsControllerTest {
    private val service = mockk<WorkoutService>()
    private val mvc = MockMvcBuilders.standaloneSetup(WorkoutsController(service)).build()

    @Test
    fun `multipart creation accepts offset timestamp optional photo and returns created record`() {
        val instant = Instant.parse("2026-09-21T10:30:00Z")
        every { service.create(any(), any()) } returns WorkoutDto(1, WorkoutCategory.GYM, instant, 45, null, null, null, null)
        val workout =
            MockMultipartFile(
                "workout",
                "",
                "application/json",
                """{"category":"GYM","startedAt":"2026-09-21T07:30:00-03:00","durationMinutes":45}""".toByteArray(),
            )
        mvc
            .perform(multipart("/api/workouts").file(workout))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(1))
        verify { service.create(match { it.startedAt == instant && it.category == WorkoutCategory.GYM }, null) }
        mvc
            .perform(multipart("/api/workouts").file(workout).file(MockMultipartFile("photo", "photo.jpg", "image/jpeg", byteArrayOf(1))))
            .andExpect(status().isCreated)
        verify { service.create(any(), match { it.originalFilename == "photo.jpg" }) }
    }

    @Test
    fun `malformed or missing required data is rejected`() {
        for (json in listOf(
            """{"category":"UNKNOWN","startedAt":"2026-09-21T10:00:00Z","durationMinutes":30}""",
            """{"category":"GYM","durationMinutes":30}""",
            """{"category":"GYM","startedAt":"not-a-date","durationMinutes":30}""",
        )) {
            mvc
                .perform(multipart("/api/workouts").file(MockMultipartFile("workout", "", "application/json", json.toByteArray())))
                .andExpect(status().isBadRequest)
        }
        verify(exactly = 0) { service.create(any(), any()) }
    }

    @Test
    fun `fractional counts and durations are rejected instead of silently truncated`() {
        for (fields in listOf("\"durationMinutes\":1.5", "\"durationMinutes\":10,\"jumps\":2.5")) {
            val json = """{"category":"JUMP_ROPE","startedAt":"2026-09-21T10:00:00Z",$fields}"""
            mvc
                .perform(multipart("/api/workouts").file(MockMultipartFile("workout", "", "application/json", json.toByteArray())))
                .andExpect(status().isBadRequest)
        }
        verify(exactly = 0) { service.create(any(), any()) }
    }

    @Test
    fun `validation errors expose actionable detail`() {
        every { service.create(any(), any()) } throws
            org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST,
                "Informe uma duração positiva em minutos.",
            )
        val json = """{"category":"GYM","startedAt":"2026-09-21T10:00:00Z","durationMinutes":0}"""
        mvc
            .perform(multipart("/api/workouts").file(MockMultipartFile("workout", "", "application/json", json.toByteArray())))
            .andExpect(status().isBadRequest)
            .andExpect(jsonPath("$.detail").value("Informe uma duração positiva em minutos."))
    }

    @Test
    fun `history binds filter and pagination and rejects unknown categories`() {
        every { service.history(WorkoutCategory.JUMP_ROPE, 1, 24) } returns WorkoutHistoryResponse(emptyList(), null)
        mvc
            .perform(get("/api/workouts").param("category", "JUMP_ROPE").param("page", "1"))
            .andExpect(status().isOk)
            .andExpect(jsonPath("$.items").isEmpty)
        mvc.perform(get("/api/workouts").param("category", "OTHER")).andExpect(status().isBadRequest)
    }
}
