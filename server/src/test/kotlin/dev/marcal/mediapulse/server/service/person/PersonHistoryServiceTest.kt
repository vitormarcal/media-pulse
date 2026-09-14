package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.people.PersonHistoryCategory
import dev.marcal.mediapulse.server.api.people.PersonHistoryPageResponse
import dev.marcal.mediapulse.server.repository.PersonHistoryRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PersonHistoryServiceTest {
    private val repository = mockk<PersonHistoryRepository>()
    private val service = PersonHistoryService(repository)

    @Test
    fun `pagination inputs are bounded`() {
        every {
            repository.findMostPresent(PersonHistoryCategory.DIRECTING, 40, 0)
        } returns PersonHistoryPageResponse(emptyList(), null)

        val response = service.mostPresent(PersonHistoryCategory.DIRECTING, limit = 999, offset = -4)

        assertEquals(emptyList(), response.items)
        verify(exactly = 1) { repository.findMostPresent(PersonHistoryCategory.DIRECTING, 40, 0) }
    }
}
