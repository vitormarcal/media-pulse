package dev.marcal.mediapulse.server.service.workout

import dev.marcal.mediapulse.server.api.workout.WorkoutCreateRequest
import dev.marcal.mediapulse.server.api.workout.WorkoutDto
import dev.marcal.mediapulse.server.model.workout.Workout
import dev.marcal.mediapulse.server.model.workout.WorkoutCategory
import dev.marcal.mediapulse.server.repository.crud.WorkoutRepository
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.server.ResponseStatusException
import java.math.BigDecimal
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkoutServiceTest {
    private val repository = mockk<WorkoutRepository>()
    private val photos = mockk<WorkoutPhotoService>(relaxed = true)
    private val tx = mockk<TxUtil>()
    private val service = WorkoutService(repository, photos, tx)
    private val draft = WorkoutCreateRequest(WorkoutCategory.RUNNING, Instant.parse("2026-09-21T10:30:00Z"), 32, BigDecimal("5.125"))

    init {
        every { tx.inTx<WorkoutDto>(any()) } answers { firstArg<() -> WorkoutDto>().invoke() }
        every { repository.saveAndFlush(any()) } answers { firstArg<Workout>().copy(id = 7) }
    }

    @Test
    fun `preserves start instant and distance and trims optional location`() {
        val result = service.create(draft.copy(location = "  Parque  "), null)
        assertEquals(draft.startedAt, result.startedAt)
        assertEquals(BigDecimal("5.125"), result.distanceKm)
        assertEquals("Parque", result.location)
        assertEquals(null, result.photoUrl)
    }

    @Test
    fun `rope accepts missing jumps and gym only needs common fields`() {
        for (category in listOf(WorkoutCategory.JUMP_ROPE, WorkoutCategory.GYM)) {
            val result = service.create(draft.copy(category = category, distanceKm = null, location = "  "), null)
            assertEquals(null, result.jumps)
            assertEquals(null, result.distanceKm)
            assertEquals(null, result.location)
        }
        assertEquals(200, service.create(draft.copy(category = WorkoutCategory.JUMP_ROPE, distanceKm = null, jumps = 200), null).jumps)
    }

    @Test
    fun `invalid metrics never persist or save a photo`() {
        val invalid =
            listOf(
                draft.copy(durationMinutes = 0),
                draft.copy(durationMinutes = -1),
                draft.copy(distanceKm = null),
                draft.copy(distanceKm = BigDecimal.ZERO),
                draft.copy(distanceKm = BigDecimal("0.0001")),
                draft.copy(distanceKm = BigDecimal("10000000")),
                draft.copy(jumps = 10),
                draft.copy(category = WorkoutCategory.GYM),
                draft.copy(category = WorkoutCategory.JUMP_ROPE, distanceKm = null, jumps = 0),
                draft.copy(location = "a".repeat(201)),
            )
        for (request in invalid) {
            assertEquals(400, assertFailsWith<ResponseStatusException> { service.create(request, null) }.statusCode.value())
        }
        verify(exactly = 0) { repository.saveAndFlush(any()) }
        verify(exactly = 0) { photos.save(any()) }
    }

    @Test
    fun `failed transaction cleans up saved photo`() {
        val photo = MockMultipartFile("photo", byteArrayOf(1))
        every { photos.save(photo) } returns "/covers/workouts/test.jpg"
        every { tx.inTx<WorkoutDto>(any()) } throws IllegalStateException("commit failed")
        assertFailsWith<IllegalStateException> { service.create(draft, photo) }
        verify { photos.delete("/covers/workouts/test.jpg") }
    }

    @Test
    fun `history rejects invalid pagination`() {
        for ((page, limit) in listOf(-1 to 24, 0 to 0, 0 to 101)) {
            assertEquals(400, assertFailsWith<ResponseStatusException> { service.history(null, page, limit) }.statusCode.value())
        }
    }
}
