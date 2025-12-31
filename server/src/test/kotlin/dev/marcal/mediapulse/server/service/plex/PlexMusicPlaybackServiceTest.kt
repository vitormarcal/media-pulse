package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.config.JacksonConfig
import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.model.music.Track
import dev.marcal.mediapulse.server.model.plex.PlexEventType
import dev.marcal.mediapulse.server.repository.crud.EventSourceCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.service.plex.util.PlexGuidExtractor
import io.mockk.Called
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.slot
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.data.repository.findByIdOrNull
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlexMusicPlaybackServiceTest {
    private lateinit var canonical: CanonicalizationService
    private lateinit var playbackRepo: TrackPlaybackCrudRepository
    private lateinit var plexArtworkService: PlexArtworkService
    private lateinit var eventSourceCrudRepository: EventSourceCrudRepository
    private lateinit var service: PlexMusicPlaybackService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        canonical = mockk(relaxed = true)
        playbackRepo = mockk(relaxed = true)
        plexArtworkService = mockk(relaxed = true)
        eventSourceCrudRepository = mockk(relaxed = true)

        service =
            PlexMusicPlaybackService(
                canonical = canonical,
                trackPlaybackRepo = playbackRepo,
                plexArtworkService = plexArtworkService,
                eventSourceCrudRepository = eventSourceCrudRepository,
            )

        every { eventSourceCrudRepository.findByIdOrNull(any()) } returns null
        mockkObject(PlexGuidExtractor)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `deve processar media_scrobble de track e inserir playback`() =
        runBlocking {
            // given
            val payload =
                samplePayload(
                    event = PlexEventType.SCROBBLE.type,
                    type = "track",
                    withInstant = true,
                )

            val artist = Artist(id = 10, name = "Therion", fingerprint = "fp-artist")
            every {
                canonical.ensureArtist(
                    name = "Therion",
                    musicbrainzId = null,
                    spotifyId = null,
                )
            } returns artist

            // capture do Artist passado para ensureAlbum
            val albumArtistSlot = slot<Artist>()
            val album =
                Album(
                    id = 20,
                    artistId = 10,
                    title = "Cover Songs 1993–2007",
                    year = 2020,
                    coverUrl = null,
                    fingerprint = "fp-album",
                )

            every {
                canonical.ensureAlbum(
                    artist = capture(albumArtistSlot),
                    title = "Cover Songs 1993–2007",
                    year = 2020,
                    coverUrl = null,
                    musicbrainzId = null,
                    spotifyId = null,
                )
            } returns album

            every { PlexGuidExtractor.extractGuids(any()) } returns
                mapOf("mbid" to "2ccf8d0b-8724-456d-b8b4-7820c87974c2")

            // capture do Artist passado para ensureTrack (agora é artist, não album)
            val trackArtistSlot = slot<Artist>()
            val track =
                Track(
                    id = 30,
                    artistId = 10,
                    title = "Summernight City (2001)",
                    durationMs = null,
                    fingerprint = "fp-track",
                )

            every {
                canonical.ensureTrack(
                    artist = capture(trackArtistSlot),
                    title = "Summernight City (2001)",
                    durationMs = null,
                    musicbrainzId = "2ccf8d0b-8724-456d-b8b4-7820c87974c2",
                    spotifyId = null,
                )
            } returns track

            every {
                canonical.linkTrackToAlbum(
                    album = any(),
                    track = any(),
                    discNumber = any(),
                    trackNumber = any(),
                )
            } returns Unit

            every {
                playbackRepo.insertIgnore(
                    trackId = any(),
                    albumId = any(),
                    source = any(),
                    sourceEventId = any(),
                    playedAt = any(),
                )
            } returns Unit

            val result = service.processScrobble(payload, eventId = 1234L)

            assertNotNull(result)
            assertEquals(30, result.trackId)
            assertEquals(20, result.albumId)
            assertEquals(PlaybackSource.PLEX, result.source)
            assertEquals(1234L, result.sourceEventId)
            assertEquals(payload.metadata.lastViewedAt, result.playedAt)

            assertEquals(10, albumArtistSlot.captured.id)
            assertEquals(10, trackArtistSlot.captured.id)

            verify(exactly = 1) {
                canonical.ensureArtist(
                    name = "Therion",
                    musicbrainzId = null,
                    spotifyId = null,
                )
            }

            verify(exactly = 1) {
                canonical.ensureAlbum(
                    artist = any(),
                    title = "Cover Songs 1993–2007",
                    year = 2020,
                    coverUrl = null,
                    musicbrainzId = null,
                    spotifyId = null,
                )
            }

            verify(exactly = 1) {
                canonical.ensureTrack(
                    artist = any(),
                    title = "Summernight City (2001)",
                    durationMs = null,
                    musicbrainzId = "2ccf8d0b-8724-456d-b8b4-7820c87974c2",
                    spotifyId = null,
                )
            }

            verify(exactly = 1) {
                canonical.linkTrackToAlbum(
                    album = album,
                    track = track,
                    discNumber = 1,
                    trackNumber = 2,
                )
            }

            verify(exactly = 1) {
                playbackRepo.insertIgnore(
                    trackId = 30,
                    albumId = 20,
                    source = PlaybackSource.PLEX.name,
                    sourceEventId = 1234L,
                    playedAt = payload.metadata.lastViewedAt!!,
                )
            }
        }

    @Test
    fun `deve retornar null quando metadata type nao for track`() =
        runBlocking {
            val payload = samplePayload(event = PlexEventType.SCROBBLE.type, type = "episode", withInstant = true)
            val result = service.processScrobble(payload, eventId = 1L)
            assertNull(result)
            verify { playbackRepo wasNot Called }
            verify { canonical wasNot Called }
        }

    @Test
    fun `deve retornar null quando event nao for scrobble`() =
        runBlocking {
            val payload = samplePayload(event = "media.play", type = "track", withInstant = true)
            val result = service.processScrobble(payload, eventId = 1L)
            assertNull(result)
            verify { playbackRepo wasNot Called }
            verify { canonical wasNot Called }
        }

    @Test
    fun `fallback playedAt quando metadata lastViewedAt for nulo`() =
        runBlocking {
            val payload =
                samplePayload(
                    event = PlexEventType.SCROBBLE.type,
                    type = "track",
                    withInstant = false,
                )

            every { PlexGuidExtractor.extractGuids(any()) } returns emptyMap()

            val artist = Artist(id = 1, name = "x", fingerprint = "fp-a")
            val album = Album(id = 2, artistId = 1, title = "y", year = 2000, coverUrl = null, fingerprint = "fp-b")
            val track = Track(id = 3, artistId = 1, title = "z", durationMs = null, fingerprint = "fp-c")

            every { canonical.ensureArtist(any(), any(), any()) } returns artist
            every { canonical.ensureAlbum(any(), any(), any(), any(), any(), any()) } returns album
            every { canonical.ensureTrack(any(), any(), any(), any(), any()) } returns track
            every { canonical.linkTrackToAlbum(any(), any(), any(), any()) } returns Unit

            every {
                playbackRepo.insertIgnore(
                    trackId = any(),
                    albumId = any(),
                    source = any(),
                    sourceEventId = any(),
                    playedAt = any(),
                )
            } returns Unit

            val before = Instant.now()
            val result = service.processScrobble(payload, eventId = 77L)
            val after = Instant.now()

            assertNotNull(result)
            assertEquals(3, result.trackId)
            assertEquals(2, result.albumId)
            assertEquals(PlaybackSource.PLEX, result.source)

            val captured = result.playedAt
            assertNotNull(captured)
            assert(captured.isAfter(before.minusSeconds(1)) && captured.isBefore(after.plusSeconds(1)))
        }

    @Test
    fun `deve passar MBID extraido para ensureTrack`() =
        runBlocking {
            val payload =
                samplePayload(
                    event = PlexEventType.SCROBBLE.type,
                    type = "track",
                    withInstant = true,
                    withMbid = "2ccf8d0b-8724-456d-b8b4-7820c87974c2",
                )

            every { PlexGuidExtractor.extractGuids(any()) } returns
                mapOf("mbid" to "2ccf8d0b-8724-456d-b8b4-7820c87974c2")

            val artist = Artist(id = 10, name = "Therion", fingerprint = "fp-a")
            val album = Album(id = 20, artistId = 10, title = "Alb", year = 2020, coverUrl = null, fingerprint = "fp-b")

            every { canonical.ensureArtist(any(), any(), any()) } returns artist
            every { canonical.ensureAlbum(any(), any(), any(), any(), any(), any()) } returns album
            every { canonical.linkTrackToAlbum(any(), any(), any(), any()) } returns Unit
            every { playbackRepo.insertIgnore(any(), any(), any(), any(), any()) } returns Unit

            val mbidSlot = slot<String?>()
            every {
                canonical.ensureTrack(
                    artist = any(),
                    title = any(),
                    durationMs = any(),
                    musicbrainzId = captureNullable(mbidSlot),
                    spotifyId = any(),
                )
            } returns Track(id = 30, artistId = 10, title = "t", durationMs = null, fingerprint = "fp-c")

            service.processScrobble(payload, eventId = 1L)

            assertEquals("2ccf8d0b-8724-456d-b8b4-7820c87974c2", mbidSlot.captured)
        }

    // ----------------- helpers -----------------

    private fun samplePayload(
        event: String,
        type: String,
        withInstant: Boolean,
        withMbid: String? = "2ccf8d0b-8724-456d-b8b4-7820c87974c2",
    ): PlexWebhookPayload {
        val base = loadSamplePayload()
        val baseMeta = base.metadata

        val newGuidList =
            baseMeta.guidList
                .filterNot { (it.id ?: "").startsWith("mbid://") }
                .let { list ->
                    if (withMbid != null) list + PlexWebhookPayload.PlexMetadata.PlexGuidMetadata("mbid://$withMbid") else list
                }

        val newMeta =
            baseMeta.copy(
                type = type,
                lastViewedAt = if (withInstant) baseMeta.lastViewedAt else null,
                guidList = newGuidList,
            )

        return base.copy(event = event, metadata = newMeta)
    }

    private fun loadSamplePayload(resourcePath: String = "/fixtures/plex_scrobble_payload.json"): PlexWebhookPayload {
        val json =
            requireNotNull(this::class.java.getResourceAsStream(resourcePath)) {
                "Fixture não encontrada em $resourcePath"
            }.bufferedReader().use { it.readText() }

        return JacksonConfig().objectMapper().readValue(json, PlexWebhookPayload::class.java)
    }
}
