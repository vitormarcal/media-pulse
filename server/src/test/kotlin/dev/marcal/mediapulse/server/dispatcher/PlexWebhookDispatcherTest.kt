package dev.marcal.mediapulse.server.dispatcher

import com.fasterxml.jackson.databind.node.ObjectNode
import dev.marcal.mediapulse.server.config.JacksonConfig
import dev.marcal.mediapulse.server.fixture.PlexEventsFixture
import dev.marcal.mediapulse.server.service.dispatch.DispatchResult
import dev.marcal.mediapulse.server.service.plex.PlexMovieWatchService
import dev.marcal.mediapulse.server.service.plex.PlexMusicPlaybackService
import dev.marcal.mediapulse.server.service.plex.PlexWebhookDispatcher
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test
import kotlin.test.assertEquals

class PlexWebhookDispatcherTest {
    private val objectMapper = JacksonConfig().objectMapper()
    private val plexMusicPlaybackService: PlexMusicPlaybackService = mockk()
    private val plexMovieWatchService: PlexMovieWatchService = mockk()
    private val dispatcher: PlexWebhookDispatcher =
        PlexWebhookDispatcher(
            objectMapper = objectMapper,
            plexMusicPlaybackService = plexMusicPlaybackService,
            plexMovieWatchService = plexMovieWatchService,
        )

    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test dispatch with invalid payload`() =
        runBlocking {
            val invalidPayload = """{}"""

            val exception = assertThrows<IllegalStateException> { dispatcher.dispatch(invalidPayload, null) }
            assertTrue(exception.message!!.contains("Invalid webhook payload format"))
        }

    @Test
    fun `test dispatch with invalid scrobble event`() =
        runBlocking {
            val invalidScrobblePayload =
                PlexEventsFixture.musicEventsJsonNode[1].deepCopy().let {
                    (it.get("Metadata") as ObjectNode).put("type", "eventTypeXyz")
                    it.toString()
                }

            val result = dispatcher.dispatch(invalidScrobblePayload, null)
            assertEquals(DispatchResult.UNSUPPORTED, result)
        }

    @Test
    fun `test dispatch with valid scrobble event but invalid track type event`() =
        runBlocking {
            val invalidTrackPayload = PlexEventsFixture.musicEventsJsonNode[1].deepCopy().toString()

            coEvery { plexMusicPlaybackService.processScrobble(any(), any()) } throws RuntimeException("Invalid track type")
            val exception = assertThrows<RuntimeException> { dispatcher.dispatch(invalidTrackPayload, null) }
            assertTrue(exception.message!!.contains("Invalid track type"))
        }

    @Test
    fun `test dispatch with valid scrobble event but with plexMusicPlaybackService returns null `() =
        runBlocking {
            val validScrobblePayload = PlexEventsFixture.musicEventsJsonNode[1].deepCopy().toString()

            coEvery { plexMusicPlaybackService.processScrobble(any(), any()) } returns null
            val exception = assertThrows<RuntimeException> { dispatcher.dispatch(validScrobblePayload, null) }
            assertTrue(exception.message!!.contains("Track playback not found for: "))
        }

    @Test
    fun `test dispatch with valid scrobble event`() =
        runBlocking {
            val validScrobblePayload = PlexEventsFixture.musicEventsJsonNode[1].deepCopy().toString()

            coEvery { plexMusicPlaybackService.processScrobble(any(), any()) } returns mockk()
            dispatcher.dispatch(validScrobblePayload, null)
        }

    @Test
    fun `test dispatch with valid scrobble event and event id`() =
        runBlocking {
            val validScrobblePayload = PlexEventsFixture.musicEventsJsonNode[1].deepCopy().toString()

            coEvery { plexMusicPlaybackService.processScrobble(any(), any()) } returns mockk()
            dispatcher.dispatch(validScrobblePayload, eventId = 123L)
        }

    @Test
    fun `test dispatch with valid movie scrobble event`() =
        runBlocking {
            val movieScrobblePayload =
                """
                {
                  "event": "media.scrobble",
                  "Metadata": {
                    "type": "movie",
                    "title": "De Olhos Bem Fechados",
                    "originalTitle": "Eyes Wide Shut",
                    "year": 1999,
                    "summary": "desc",
                    "lastViewedAt": 1772082419,
                    "Guid": [{"id":"imdb://tt0120663"},{"id":"tmdb://345"}]
                  }
                }
                """.trimIndent()

            coEvery { plexMovieWatchService.processScrobble(any()) } returns mockk()

            val result = dispatcher.dispatch(movieScrobblePayload, eventId = 123L)
            assertEquals(DispatchResult.SUCCESS, result)
        }

    @Test
    fun `test dispatch with valid movie scrobble event but movie service returns null`() =
        runBlocking {
            val movieScrobblePayload =
                """
                {
                  "event": "media.scrobble",
                  "Metadata": {
                    "type": "movie",
                    "title": "De Olhos Bem Fechados"
                  }
                }
                """.trimIndent()

            coEvery { plexMovieWatchService.processScrobble(any()) } returns null
            val exception = assertThrows<RuntimeException> { dispatcher.dispatch(movieScrobblePayload, eventId = null) }
            assertTrue(exception.message!!.contains("Movie watch not found for: "))
        }
}
