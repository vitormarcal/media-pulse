package dev.marcal.mediapulse.server.service.eventsource

import dev.marcal.mediapulse.server.fixture.EventSourceFixture
import dev.marcal.mediapulse.server.model.EventSource
import dev.marcal.mediapulse.server.repository.crud.EventSourceCrudRepository
import dev.marcal.mediapulse.server.service.dispatch.DispatchResult
import dev.marcal.mediapulse.server.service.plex.PlexWebhookDispatcher
import dev.marcal.mediapulse.server.service.spotify.SpotifyEventDispatcher
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.springframework.data.repository.findByIdOrNull
import kotlin.test.Test

class ProcessEventSourceServiceTest {
    private val repository = mockk<EventSourceCrudRepository>()
    private val plexWebhookDispatcher = mockk<PlexWebhookDispatcher>()
    private val spotifyEventDispatcher = mockk<SpotifyEventDispatcher>()
    private lateinit var service: ProcessEventSourceService

    @BeforeEach
    fun setUp() {
        clearAllMocks()

        every { plexWebhookDispatcher.provider } returns "plex"
        every { spotifyEventDispatcher.provider } returns "spotify"

        every { repository.findByIdOrNull(any()) } returns EventSourceFixture.example()
        every { repository.save(any()) } returnsArgument 0

        coEvery { plexWebhookDispatcher.dispatch(any(), any()) } returns DispatchResult.SUCCESS
        coEvery { spotifyEventDispatcher.dispatch(any(), any()) } returns DispatchResult.SUCCESS

        service =
            ProcessEventSourceService(
                repository = repository,
                dispatchers = listOf(plexWebhookDispatcher, spotifyEventDispatcher),
            )
    }

    @Test
    fun `should skip when event not found`() =
        runBlocking {
            val eventId = 1L

            every { repository.findByIdOrNull(eventId) } returns null

            service.execute(eventId)

            verify(exactly = 0) { repository.save(any()) }
        }

    @Test
    fun `should mark event as failed when provider is unsupported`() =
        runBlocking {
            val eventId = 1L
            val event = EventSourceFixture.example().copy(provider = "unsupported")

            every { repository.findByIdOrNull(eventId) } returns event

            service.execute(eventId)

            verify {
                repository.save(
                    match<EventSource> {
                        it.status == EventSource.Status.UNSUPPORTED &&
                            it.errorMessage == "Unsupported provider: unsupported"
                    },
                )
            }
        }

    @Test
    fun `should mark event as success when provider is plex`() =
        runBlocking {
            val eventId = 1L
            val event = EventSourceFixture.example().copy(provider = "plex")

            every { repository.findByIdOrNull(eventId) } returns event

            service.execute(eventId)

            coVerify {
                plexWebhookDispatcher.dispatch(event.payload, any())
                repository.save(
                    match<EventSource> {
                        it.status == EventSource.Status.SUCCESS &&
                            it.errorMessage == null
                    },
                )
            }
        }

    @Test
    fun `should mark event as failed when an exception occurs during processing`() =
        runBlocking {
            val eventId = 1L
            val event = EventSourceFixture.example().copy(provider = "plex")

            every { repository.findByIdOrNull(eventId) } returns event
            coEvery { plexWebhookDispatcher.dispatch(any(), any()) } throws RuntimeException("Processing error")

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
    fun `should execute async processing of webhook event`() =
        runBlocking {
            val eventId = 1L
            val event = EventSourceFixture.example().copy(provider = "plex")

            every { repository.findByIdOrNull(eventId) } returns event

            service.execute(eventId)

            coVerify {
                plexWebhookDispatcher.dispatch(event.payload, any())
                repository.save(
                    match<EventSource> {
                        it.status == EventSource.Status.SUCCESS &&
                            it.errorMessage == null
                    },
                )
            }
        }
}
