package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.fixture.PlexEventsFixture
import dev.marcal.mediapulse.server.model.music.MusicSource
import dev.marcal.mediapulse.server.repository.MusicAggregationRepository
import dev.marcal.mediapulse.server.repository.crud.MusicSourceCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MusicSourceIdentifierCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository
import io.mockk.clearAllMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.assertNotNull
import org.junit.jupiter.api.assertNull
import org.junit.jupiter.api.assertThrows
import kotlin.test.Test

class PlexMusicPlaybackServiceTest {
    val musicSourceCrudRepository: MusicSourceCrudRepository = mockk()
    val trackPlaybackCrudRepository: TrackPlaybackCrudRepository = mockk()
    val musicSourceIdentifierCrudRepository: MusicSourceIdentifierCrudRepository = mockk()
    val entityManager: EntityManager = mockk()
    val service: PlexMusicPlaybackService =
        PlexMusicPlaybackService(
            musicAggregationRepository =
                MusicAggregationRepository(
                    trackPlaybackCrudRepository = trackPlaybackCrudRepository,
                    musicSourceCrudRepository = musicSourceCrudRepository,
                    musicSourceIdentifierCrudRepository = musicSourceIdentifierCrudRepository,
                    entityManager = entityManager,
                ),
        )

    val eventId = 123L

    @BeforeEach
    fun setUp() {
        every { musicSourceCrudRepository.findByFingerprint(any()) } returns null
        every { musicSourceCrudRepository.save(any()) } returnsArgument 0
        every { trackPlaybackCrudRepository.save(any()) } returnsArgument 0
        every { musicSourceIdentifierCrudRepository.save(any()) } returnsArgument 0
        every { musicSourceIdentifierCrudRepository.findByMusicSourceId(any()) } returns emptyList()
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
        Assertions.assertTrue(exception.message!!.contains("Invalid GUID format: mbid://"))
    }

    @Test
    fun `should not create music source if it already exists`() {
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
            MusicSource(
                id = 123L,
                title = payload.metadata.title,
                album = payload.metadata.parentTitle,
                artist = payload.metadata.grandparentTitle,
                year = payload.metadata.parentYear,
            )

        every { musicSourceCrudRepository.findByFingerprint(any()) } returns existingTrack

        val result = service.processScrobble(payload, eventId)

        assertNotNull(result)

        Assertions.assertTrue(result.musicSourceId == existingTrack.id)

        verify(exactly = 0) { musicSourceCrudRepository.save(any()) }
    }
}
