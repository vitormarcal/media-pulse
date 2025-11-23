package dev.marcal.mediapulse.server.service.eventsource

import dev.marcal.mediapulse.server.fixture.EventSourceFixture
import dev.marcal.mediapulse.server.model.EventSource
import dev.marcal.mediapulse.server.repository.crud.EventSourceCrudRepository
import dev.marcal.mediapulse.server.service.plex.PlexWebhookDispatcher
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.springframework.data.repository.findByIdOrNull
import kotlin.test.Test

class ProcessEventSourceServiceTest {
    private val repository = mockk<EventSourceCrudRepository>()
    private val plexWebhookDispatcher = mockk<PlexWebhookDispatcher>()
    private val service =
        ProcessEventSourceService(
            repository = repository,
            plexWebhookDispatcher = plexWebhookDispatcher,
        )

    @BeforeEach
    fun setUp() {
        clearAllMocks()
        every { repository.findByIdOrNull(any()) } returns EventSourceFixture.example()
        every { repository.save(any()) } returnsArgument 0
        every { plexWebhookDispatcher.dispatch(any(), any()) } returns mockk()
    }

    @Test
    fun `should skip when event not found`() {
        val eventId = 1L

        every { repository.findByIdOrNull(eventId) } returns null

        service.execute(eventId)

        verify(exactly = 0) { repository.save(any()) }
    }

    @Test
    fun `should mark event as failed when provider is unsupported`() {
        val eventId = 1L
        val event = EventSourceFixture.example().copy(provider = "unsupported")

        every { repository.findByIdOrNull(eventId) } returns event

        service.execute(eventId)

        verify {
            repository.save(
                match<EventSource> {
                    it.status == EventSource.Status.FAILED &&
                        it.errorMessage == "Unsupported provider: unsupported"
                },
            )
        }
    }

    @Test
    fun `should mark event as success when provider is plex`() {
        val eventId = 1L
        val event = EventSourceFixture.example().copy(provider = "plex")

        every { repository.findByIdOrNull(eventId) } returns event

        service.execute(eventId)

        verify {
            plexWebhookDispatcher.dispatch(event.payload, eventId)
            repository.save(
                match<EventSource> {
                    it.status == EventSource.Status.SUCCESS &&
                        it.errorMessage == null
                },
            )
        }
    }

    @Test
    fun `should mark event as failed when an exception occurs during processing`() {
        val eventId = 1L
        val event = EventSourceFixture.example().copy(provider = "plex")

        every { repository.findByIdOrNull(eventId) } returns event
        every { plexWebhookDispatcher.dispatch(any(), any()) } throws RuntimeException("Processing error")

        service.execute(eventId)

        verify {
            repository.save(
                match<EventSource> {
                    it.status == EventSource.Status.FAILED &&
                        it.errorMessage == "Processing error"
                },
            )
        }
    }

    @Test
    fun `should execute async processing of webhook event`() {
        val eventId = 1L
        val event = EventSourceFixture.example().copy(provider = "plex")

        every { repository.findByIdOrNull(eventId) } returns event

        service.executeAsync(eventId)

        verify {
            plexWebhookDispatcher.dispatch(event.payload, eventId)
            repository.save(
                match<EventSource> {
                    it.status == EventSource.Status.SUCCESS &&
                        it.errorMessage == null
                },
            )
        }
    }
}
