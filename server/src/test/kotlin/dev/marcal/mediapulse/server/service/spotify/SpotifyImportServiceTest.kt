package dev.marcal.mediapulse.server.service.spotify

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.integration.spotify.SpotifyApiClient
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyRecentlyPlayedItem
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyRecentlyPlayedResponse
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyTrack
import dev.marcal.mediapulse.server.repository.spotify.SpotifySyncStateRepository
import dev.marcal.mediapulse.server.service.eventsource.EventSourceService
import dev.marcal.mediapulse.server.service.eventsource.ProcessEventSourceService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.confirmVerified
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals

class SpotifyImportServiceTest {
    @MockK lateinit var spotifyApi: SpotifyApiClient

    @MockK lateinit var syncRepo: SpotifySyncStateRepository

    @MockK lateinit var eventSourceService: EventSourceService

    @MockK lateinit var processEventSourceService: ProcessEventSourceService

    @MockK lateinit var objectMapper: ObjectMapper

    private lateinit var service: SpotifyImportService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)

        service =
            SpotifyImportService(
                spotifyApi = spotifyApi,
                syncRepo = syncRepo,
                eventSourceService = eventSourceService,
                processEventSourceService = processEventSourceService,
                objectMapper = objectMapper,
            )
    }

    @Test
    fun `should return -1 and do nothing when import is already running`() =
        runBlocking {
            val f = service.javaClass.getDeclaredField("running")
            f.isAccessible = true
            val running = f.get(service) as AtomicBoolean
            running.set(true)

            val out = service.importRecentlyPlayed(resetCursor = false, maxPages = null)

            assertEquals(-1, out)

            coVerify(exactly = 0) { spotifyApi.getRecentlyPlayed(any(), any()) }
            verify(exactly = 0) { syncRepo.updateCursor(any()) }
            verify(exactly = 0) { syncRepo.getOrCreateSingleton() }
            verify(exactly = 0) { objectMapper.writeValueAsString(any()) }
            verify(exactly = 0) { eventSourceService.save(any(), any(), any()) }
            verify(exactly = 0) { processEventSourceService.execute(any()) }

            confirmVerified(
                spotifyApi,
                syncRepo,
                eventSourceService,
                processEventSourceService,
                objectMapper,
            )
        }

    @Test
    fun `should import items, skip null track id, paginate, and update cursor`() =
        runBlocking {
            every { syncRepo.getOrCreateSingleton() } returns
                dev.marcal.mediapulse.server.model.spotify.SpotifySyncState(
                    id = 1L,
                    cursorAfterMs = 0L,
                )

            every { syncRepo.updateCursor(any()) } returns
                dev.marcal.mediapulse.server.model.spotify.SpotifySyncState(
                    id = 1L,
                    cursorAfterMs = 999L,
                )

            val item1PlayedAt = "2020-01-01T00:00:01Z"
            val item2PlayedAt = "2020-01-01T00:00:10Z"

            val item1 =
                SpotifyRecentlyPlayedItem(
                    playedAt = item1PlayedAt,
                    track =
                        SpotifyTrack(
                            id = "t1",
                            name = "n1",
                            durationMs = 123,
                            trackNumber = 1,
                            discNumber = 1,
                            artists = null,
                            album = null,
                        ),
                )

            val item2 =
                SpotifyRecentlyPlayedItem(
                    playedAt = item2PlayedAt,
                    track =
                        SpotifyTrack(
                            id = null, // será ignorado
                            name = "n2",
                            durationMs = 456,
                            trackNumber = 2,
                            discNumber = 1,
                            artists = null,
                            album = null,
                        ),
                )

            val expectedMaxSeenMs =
                java.time.Instant
                    .parse(item1PlayedAt)
                    .toEpochMilli()
            val expectedNextAfterMs = expectedMaxSeenMs + 1

            coEvery { spotifyApi.getRecentlyPlayed(afterMs = 0L, limit = 50) } returns
                SpotifyRecentlyPlayedResponse(items = listOf(item1, item2))

            coEvery { spotifyApi.getRecentlyPlayed(afterMs = expectedNextAfterMs, limit = 50) } returns
                SpotifyRecentlyPlayedResponse(items = emptyList())

            every { objectMapper.writeValueAsString(any()) } returns """{"x":"y"}"""

            val savedEvent = io.mockk.mockk<dev.marcal.mediapulse.server.model.EventSource>()
            every { savedEvent.id } returns 777L

            every {
                eventSourceService.save(
                    provider = "spotify",
                    payload = any(),
                    fingerprint = null,
                )
            } returns savedEvent

            every { processEventSourceService.execute(any()) } just runs

            val imported = service.importRecentlyPlayed(resetCursor = true, maxPages = null)

            assertEquals(1, imported)

            verify(exactly = 1) { syncRepo.updateCursor(0L) }
            verify(exactly = 1) { syncRepo.getOrCreateSingleton() }

            coVerify(exactly = 1) { spotifyApi.getRecentlyPlayed(afterMs = 0L, limit = 50) }
            coVerify(exactly = 1) { spotifyApi.getRecentlyPlayed(afterMs = expectedNextAfterMs, limit = 50) }

            verify(exactly = 1) { objectMapper.writeValueAsString(match { it is SpotifyRecentlyPlayedItem }) }

            verify(exactly = 1) {
                eventSourceService.save(
                    provider = "spotify",
                    payload = any(),
                    fingerprint = null,
                )
            }
            verify(exactly = 1) { processEventSourceService.execute(777L) }

            verify(exactly = 1) { syncRepo.updateCursor(expectedNextAfterMs) }
        }
}
