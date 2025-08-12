package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.fixture.PlexEventsFixture
import dev.marcal.mediapulse.server.model.music.CanonicalTrack
import dev.marcal.mediapulse.server.repository.CanonicalTrackRepository
import dev.marcal.mediapulse.server.repository.TrackPlaybackRepository
import dev.marcal.mediapulse.server.repository.crud.CanonicalTrackCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class PlexMusicPlaybackServiceTest {
    val canonicalTrackCrudRepository: CanonicalTrackCrudRepository = mockk()
    val trackPlaybackCrudRepository: TrackPlaybackCrudRepository = mockk()
    val service: PlexMusicPlaybackService =
        PlexMusicPlaybackService(
            canonicalTrackRepository = CanonicalTrackRepository(canonicalTrackCrudRepository),
            trackPlaybackRepository = TrackPlaybackRepository(trackPlaybackCrudRepository),
        )

    val eventId = 123L

    @BeforeEach
    fun setUp() {
        every { canonicalTrackCrudRepository.findByCanonicalIdAndCanonicalType(any(), any()) } returns null
        every { canonicalTrackCrudRepository.save(any()) } returnsArgument 0
        every { trackPlaybackCrudRepository.save(any()) } returnsArgument 0
    }

    @AfterEach
    fun tearDown() {
        clearAllMocks()
    }

    @Test
    fun `should parse playback scrobble event correctly`() {
        val payload = PlexEventsFixture.musicEvents[1]
        val event = service.processScrobble(payload, eventId)
        assertNotNull(event, "Event should not be null for payload: $payload")
    }

    @Test
    fun `should process without eventId`() {
        val payload = PlexEventsFixture.musicEvents[1]
        val event = service.processScrobble(payload, null)
        assertNotNull(event, "Event should not be null for payload: $payload")
    }

    @Test
    fun `should ignore media play event`() {
        val payload = PlexEventsFixture.musicEvents[0]
        val event = service.processScrobble(payload, eventId)
        assertNull(event, "Event should not be null for payload: $payload")
    }

    @Test
    fun `should ignore media stop event`() {
        val payload = PlexEventsFixture.musicEvents[2]
        val event = service.processScrobble(payload, eventId)
        assertNull(event, "Event should not be null for payload: $payload")
    }

    @Test
    fun `should return null when meta type is not track`() {
        val payload =
            PlexEventsFixture.musicEvents
                .first()
                .let { payload -> payload.copy(metadata = payload.metadata.copy(type = "movie")) }
        val result = service.processScrobble(payload, eventId)
        assertNull(result, "Event should returns null when meta.type is not 'track'")
    }

    @Test
    fun `should throw exception when eventType is null`() {
        val payload =
            PlexEventsFixture.musicEvents.first().let { payload ->
                payload.copy(event = "unsupported.event", metadata = payload.metadata.copy(type = "track"))
            }

        val exception =
            assertThrows<IllegalArgumentException> {
                service.processScrobble(payload, eventId)
            }
        Assertions.assertTrue(exception.message!!.contains("event type is not supported"))
    }

    @Test
    fun `should throw exception when mbid is missing`() {
        val payload =
            PlexEventsFixture.musicEvents.first().let { original ->
                val metadataWithoutMbid =
                    original.metadata.copy(
                        guid = original.metadata.guid.filterNot { it.id.startsWith("mbid://") },
                    )
                original.copy(
                    event = "media.scrobble",
                    metadata = metadataWithoutMbid,
                )
            }

        val exception =
            assertThrows<IllegalArgumentException> {
                service.processScrobble(payload, eventId)
            }
        Assertions.assertTrue(exception.message!!.contains("MBID missing"))
    }

    @Test
    fun `should throw exception when mbid is present but malformed`() {
        val payload =
            PlexEventsFixture.musicEvents.first().let { original ->
                val metadataWithMalformedMbid =
                    original.metadata.copy(
                        guid =
                            original.metadata.guid.map {
                                if (it.id.startsWith("mbid://")) it.copy(id = "mbid://") else it
                            },
                    )
                original.copy(
                    event = "media.scrobble",
                    metadata = metadataWithMalformedMbid,
                )
            }

        val exception =
            assertThrows<IllegalArgumentException> {
                service.processScrobble(payload, eventId)
            }
        Assertions.assertTrue(exception.message!!.contains("MBID is empty or malformed"))
    }

    @Test
    fun `should not create canonicalTrack if it already exists`() {
        val payload =
            PlexEventsFixture.musicEvents
                .first()
                .copy(
                    event = "media.scrobble",
                    metadata =
                        PlexEventsFixture.musicEvents
                            .first()
                            .metadata
                            .copy(type = "track"),
                )
        val existingTrack =
            CanonicalTrack(
                id = 123L,
                canonicalId = "some-mbid",
                canonicalType = "MBID",
                title = payload.metadata.title,
                album = payload.metadata.parentTitle,
                artist = payload.metadata.grandparentTitle,
                year = payload.metadata.parentYear,
            )

        every { canonicalTrackCrudRepository.findByCanonicalIdAndCanonicalType(any(), any()) } returns existingTrack

        val result = service.processScrobble(payload, eventId)

        assertNotNull(result)

        Assertions.assertTrue(result.canonicalTrackId == existingTrack.id)

        verify(exactly = 0) { canonicalTrackCrudRepository.save(any()) }
    }
}
