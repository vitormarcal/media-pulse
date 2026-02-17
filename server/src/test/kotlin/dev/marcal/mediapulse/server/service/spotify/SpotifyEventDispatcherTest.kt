package dev.marcal.mediapulse.server.service.spotify

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyRecentlyPlayedItem
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyTrack
import dev.marcal.mediapulse.server.service.dispatch.DispatchResult
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class SpotifyEventDispatcherTest {
    @MockK lateinit var spotifyPlaybackService: SpotifyPlaybackService

    private lateinit var dispatcher: SpotifyEventDispatcher
    private lateinit var objectMapper: ObjectMapper

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        objectMapper = ObjectMapper()
        objectMapper.registerModule(
            com.fasterxml.jackson.module.kotlin.KotlinModule
                .Builder()
                .build(),
        )
        dispatcher =
            SpotifyEventDispatcher(
                objectMapper = objectMapper,
                spotifyPlaybackService = spotifyPlaybackService,
            )
    }

    @Test
    fun `should deserialize payload and dispatch to playback service`() =
        runBlocking {
            val item =
                SpotifyRecentlyPlayedItem(
                    playedAt = "2020-01-01T00:00:00Z",
                    track =
                        SpotifyTrack(
                            id = "track123",
                            name = "Track Name",
                            durationMs = 180000,
                            trackNumber = 1,
                            discNumber = 1,
                            artists = null,
                            album = null,
                        ),
                )

            val payload = objectMapper.writeValueAsString(item)

            coEvery { spotifyPlaybackService.processRecentlyPlayedItem(any(), any()) } just runs

            val result = dispatcher.dispatch(payload, eventId = 123L)

            assertEquals(DispatchResult.SUCCESS, result)
            coVerify(exactly = 1) { spotifyPlaybackService.processRecentlyPlayedItem(any(), 123L) }
        }

    @Test
    fun `should pass null eventId when not provided`() =
        runBlocking {
            val item =
                SpotifyRecentlyPlayedItem(
                    playedAt = "2020-01-01T00:00:00Z",
                    track =
                        SpotifyTrack(
                            id = "track123",
                            name = "Track Name",
                            durationMs = 180000,
                            trackNumber = 1,
                            discNumber = 1,
                            artists = null,
                            album = null,
                        ),
                )

            val payload = objectMapper.writeValueAsString(item)

            coEvery { spotifyPlaybackService.processRecentlyPlayedItem(any(), any()) } just runs

            val result = dispatcher.dispatch(payload, eventId = null)

            assertEquals(DispatchResult.SUCCESS, result)
            coVerify(exactly = 1) { spotifyPlaybackService.processRecentlyPlayedItem(any(), null) }
        }

    @Test
    fun `should return SUCCESS on dispatch`() =
        runBlocking {
            val item =
                SpotifyRecentlyPlayedItem(
                    playedAt = "2020-01-01T00:00:00Z",
                    track =
                        SpotifyTrack(
                            id = "track123",
                            name = "Track Name",
                            durationMs = 180000,
                            trackNumber = 1,
                            discNumber = 1,
                            artists = null,
                            album = null,
                        ),
                )

            val payload = objectMapper.writeValueAsString(item)

            coEvery { spotifyPlaybackService.processRecentlyPlayedItem(any(), any()) } just runs

            val result = dispatcher.dispatch(payload, eventId = 999L)

            assertEquals(DispatchResult.SUCCESS, result)
        }
}
