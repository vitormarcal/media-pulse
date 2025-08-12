package dev.marcal.mediapulse.server.service.eventsource

import dev.marcal.mediapulse.server.MediapulseServerApplicationTests
import dev.marcal.mediapulse.server.fixture.EventSourceFixture
import dev.marcal.mediapulse.server.model.EventSource
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.springframework.beans.factory.annotation.Autowired
import kotlin.test.assertEquals

class ProcessEventSourceServiceIT : MediapulseServerApplicationTests() {
    @Autowired
    private lateinit var service: ProcessEventSourceService

    @Test
    fun `should process event source successfully`() {
        val saved = eventSourceCrudRepository.save(EventSourceFixture.example())
        service.execute(saved.id)

        val updated = assertDoesNotThrow { eventSourceCrudRepository.findById(saved.id).orElseThrow() }

        assertEquals(EventSource.Status.SUCCESS, updated.status)
        assertEquals(1, trackPlaybackCrudRepository.count())
        assertEquals(1, canonicalTrackCrudRepository.count())
    }

    @Test
    fun `should handle unsupported provider gracefully`() {
        val saved = eventSourceCrudRepository.save(EventSourceFixture.example().copy(provider = "unsupported"))
        service.execute(saved.id)

        val updated = assertDoesNotThrow { eventSourceCrudRepository.findById(saved.id).orElseThrow() }

        assertEquals(EventSource.Status.FAILED, updated.status)
        assertEquals("Unsupported provider: unsupported", updated.errorMessage)
    }
}
