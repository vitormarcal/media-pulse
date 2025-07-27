package dev.marcal.mediapulse.server.dispatcher

import com.fasterxml.jackson.databind.node.ObjectNode
import dev.marcal.mediapulse.server.config.JacksonConfig
import dev.marcal.mediapulse.server.fixture.PlexEventsFixture
import dev.marcal.mediapulse.server.service.plex.PlexMusicPlaybackService
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class PlexWebhookDispatcherTest {
    private val objectMapper = JacksonConfig().objectMapper()
    private val plexMusicPlaybackService: PlexMusicPlaybackService = mockk()
    private val dispatcher: PlexWebhookDispatcher =
        PlexWebhookDispatcher(
            objectMapper = objectMapper,
            plexMusicPlaybackService = plexMusicPlaybackService,
        )

    @BeforeEach
    fun setUp() {
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `test dispatch with invalid payload`() {
        val invalidPayload = """{}"""

        val exception = assertThrows<IllegalStateException> { dispatcher.dispatch(invalidPayload, null) }
        assertTrue(exception.message!!.contains("Invalid webhook payload format"))
    }

    @Test
    fun `test dispatch with unsupported event type`() {
        val unsupportedEventPayload =
            PlexEventsFixture.musicEventsJsonNode.first().deepCopy().let {
                it.put("event", "unsupported.event")
                it.toString()
            }

        val exception = assertThrows<IllegalStateException> { dispatcher.dispatch(unsupportedEventPayload, null) }
        assertTrue(exception.message!!.contains("Unsupported event: unsupported.event"))
    }

    @Test
    fun `test dispatch with invalid scrobble event`() {
        val invalidScrobblePayload =
            PlexEventsFixture.musicEventsJsonNode[1].deepCopy().let {
                (it.get("Metadata") as ObjectNode).put("type", "eventTypeXyz")
                it.toString()
            }

        val exception = assertThrows<IllegalStateException> { dispatcher.dispatch(invalidScrobblePayload, null) }
        assertTrue(exception.message!!.contains("Unsupported metadata type for scrobble: eventTypeXyz"))
    }

    @Test
    fun `test dispatch with valid scrobble event but invalid track type event`() {
        val invalidTrackPayload = PlexEventsFixture.musicEventsJsonNode[1].deepCopy().toString()

        every { plexMusicPlaybackService.processScrobble(any(), any()) } throws RuntimeException("Invalid track type")
        val exception = assertThrows<RuntimeException> { dispatcher.dispatch(invalidTrackPayload, null) }
        assertTrue(exception.message!!.contains("Invalid track type"))
    }

    @Test
    fun `test dispatch with valid scrobble event but with plexMusicPlaybackService returns null `() {
        val validScrobblePayload = PlexEventsFixture.musicEventsJsonNode[1].deepCopy().toString()

        every { plexMusicPlaybackService.processScrobble(any(), any()) } returns null
        val exception = assertThrows<RuntimeException> { dispatcher.dispatch(validScrobblePayload, null) }
        assertTrue(exception.message!!.contains("Track playback not found for scrobble event: "))
    }

    @Test
    fun `test dispatch with valid scrobble event`() {
        val validScrobblePayload = PlexEventsFixture.musicEventsJsonNode[1].deepCopy().toString()

        every { plexMusicPlaybackService.processScrobble(any(), any()) } returns mockk()
        dispatcher.dispatch(validScrobblePayload, null)
    }

    @Test
    fun `test dispatch with valid scrobble event and event id`() {
        val validScrobblePayload = PlexEventsFixture.musicEventsJsonNode[1].deepCopy().toString()

        every { plexMusicPlaybackService.processScrobble(any(), any()) } returns mockk()
        dispatcher.dispatch(validScrobblePayload, eventId = 123L)
    }
}
