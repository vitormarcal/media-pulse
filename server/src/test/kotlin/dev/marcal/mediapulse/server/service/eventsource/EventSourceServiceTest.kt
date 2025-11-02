package dev.marcal.mediapulse.server.service.eventsource

import dev.marcal.mediapulse.server.fixture.PlexEventsFixture
import dev.marcal.mediapulse.server.repository.crud.EventSourceCrudRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class EventSourceServiceTest {
    private val repository: EventSourceCrudRepository = mockk()
    private val service = EventSourceService(repository)

    @Test
    fun `should save payload when provider and payload are valid`() {
        val provider = "plex"
        val payload = PlexEventsFixture.musicEventsJson.first()

        every { repository.findByFingerprint(any()) } returns null
        every { repository.save(any()) } answers { firstArg() }

        service.save(payload, provider)

        verify { repository.save(any()) }
    }

    @Test
    fun `should not save payload when fingerprint already exists`() {
        val provider = "plex"
        val payload = PlexEventsFixture.musicEventsJson.first()

        every { repository.findByFingerprint(any()) } returns mockk()
        every { repository.save(any()) } answers { firstArg() }

        service.save(payload, provider)

        verify(exactly = 0) { repository.save(any()) }
    }
}
