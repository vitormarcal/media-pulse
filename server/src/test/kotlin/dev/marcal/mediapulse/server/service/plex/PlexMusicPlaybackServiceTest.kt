package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.config.JacksonConfig
import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.model.music.Track
import dev.marcal.mediapulse.server.model.music.TrackPlayback
import dev.marcal.mediapulse.server.model.plex.PlexEventType
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
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlexMusicPlaybackServiceTest {
    private lateinit var canonical: CanonicalizationService
    private lateinit var playbackRepo: TrackPlaybackCrudRepository
    private lateinit var service: PlexMusicPlaybackService

    @BeforeEach
    fun setup() {
        MockKAnnotations.init(this, relaxUnitFun = true)
        canonical = mockk(relaxed = true)
        playbackRepo = mockk(relaxed = true)
        service = PlexMusicPlaybackService(canonical, playbackRepo)
        mockkObject(PlexGuidExtractor)
    }

    @AfterEach
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `deve processar media_scrobble de track e salvar playback`() {
        // given
        val payload =
            samplePayload(
                event = PlexEventType.SCROBBLE.type, // "media.scrobble"
                type = "track",
                withInstant = true,
            )

        every {
            canonical.ensureArtist(
                name = "Therion",
                musicbrainzId = null,
                plexGuid = "plex://artist/ART123",
                spotifyId = null,
            )
        } returns Artist(id = 10, name = "Therion", fingerprint = "fp-artist")

        // capture do Artist passado para ensureAlbum
        val albumArtistSlot = slot<Artist>()
        every {
            canonical.ensureAlbum(
                artist = capture(albumArtistSlot),
                title = "Cover Songs 1993–2007",
                year = 2020,
                coverUrl = "/library/metadata/10210/thumb/1751786095",
                musicbrainzId = null,
                plexGuid = "plex://album/ALB456",
                spotifyId = null,
            )
        } returns Album(id = 20, artistId = 10, title = "Cover Songs 1993–2007", year = 2020, coverUrl = null, fingerprint = "fp-album")

        every { PlexGuidExtractor.extractGuids(any()) } returns mapOf("mbid" to "2ccf8d0b-8724-456d-b8b4-7820c87974c2")

        // capture do Album passado para ensureTrack
        val trackAlbumSlot = slot<Album>()
        every {
            canonical.ensureTrack(
                album = capture(trackAlbumSlot),
                title = "Summernight City (2001)",
                trackNumber = 2,
                discNumber = 1,
                durationMs = null,
                musicbrainzId = "2ccf8d0b-8724-456d-b8b4-7820c87974c2",
                plexGuid = "plex://track/TRK789",
                spotifyId = null,
            )
        } returns
            Track(
                id = 30,
                albumId = 20,
                title = "Summernight City (2001)",
                trackNumber = 2,
                discNumber = 1,
                durationMs = null,
                fingerprint = "fp-track",
            )

        val playbackSlot = slot<TrackPlayback>()
        every { playbackRepo.save(capture(playbackSlot)) } answers { playbackSlot.captured.copy(id = 999) }

        // when
        val result = service.processScrobble(payload, eventId = 1234L)

        // then
        assertNotNull(result)
        assertEquals(999, result.id)
        assertEquals(30, result.trackId)
        assertEquals(PlaybackSource.PLEX, result.source)
        assertEquals(1234L, result.sourceEventId)
        assertEquals(payload.metadata.lastViewedAt, result.playedAt)

        // valida capturas
        assertEquals(10, albumArtistSlot.captured.id)
        assertEquals(20, trackAlbumSlot.captured.id)

        verify(exactly = 1) { canonical.ensureArtist(any(), any(), any(), any()) }
        verify(exactly = 1) { canonical.ensureAlbum(any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { canonical.ensureTrack(any(), any(), any(), any(), any(), any(), any(), any()) }
        verify(exactly = 1) { playbackRepo.save(any()) }
    }

    @Test
    fun `deve retornar null quando metadata type nao for track`() {
        val payload = samplePayload(event = PlexEventType.SCROBBLE.type, type = "episode", withInstant = true)
        val result = service.processScrobble(payload, eventId = 1L)
        assertNull(result)
        verify { playbackRepo wasNot Called }
        verify { canonical wasNot Called }
    }

    @Test
    fun `deve retornar null quando event nao for scrobble`() {
        val payload = samplePayload(event = "media.play", type = "track", withInstant = true)
        val result = service.processScrobble(payload, eventId = 1L)
        assertNull(result)
        verify { playbackRepo wasNot Called }
        verify { canonical wasNot Called }
    }

    @Test
    fun `fallback playedAt quando metadata lastViewedAt for nulo`() {
        val payload =
            samplePayload(
                event = PlexEventType.SCROBBLE.type,
                type = "track",
                withInstant = false,
            )

        every { PlexGuidExtractor.extractGuids(any()) } returns emptyMap()
        every { canonical.ensureArtist(any(), any(), any(), any()) } returns Artist(id = 1, name = "x", fingerprint = "fp-a")
        every { canonical.ensureAlbum(any(), any(), any(), any(), any(), any(), any()) } returns
            Album(id = 2, artistId = 1, title = "y", year = 2000, coverUrl = null, fingerprint = "fp-b")
        every { canonical.ensureTrack(any(), any(), any(), any(), any(), any(), any(), any()) } returns
            Track(id = 3, albumId = 2, title = "z", trackNumber = 1, discNumber = 1, durationMs = null, fingerprint = "fp-c")

        val slot = slot<TrackPlayback>()
        every { playbackRepo.save(capture(slot)) } answers { slot.captured.copy(id = 42) }

        val before = Instant.now()
        val result = service.processScrobble(payload, eventId = 77L)
        val after = Instant.now()

        assertNotNull(result)
        assertEquals(42, result.id)
        assertEquals(3, result.trackId)
        assertEquals(PlaybackSource.PLEX, result.source)

        val captured = slot.captured.playedAt
        assertNotNull(captured)
        assert(captured.isAfter(before.minusSeconds(1)) && captured.isBefore(after.plusSeconds(1)))
    }

    @Test
    fun `deve passar MBID extraido para ensureTrack`() {
        val payload =
            samplePayload(
                event = PlexEventType.SCROBBLE.type,
                type = "track",
                withInstant = true,
                withMbid = "2ccf8d0b-8724-456d-b8b4-7820c87974c2",
            )

        every { PlexGuidExtractor.extractGuids(any()) } returns
            mapOf("mbid" to "2ccf8d0b-8724-456d-b8b4-7820c87974c2", "plex" to "track/TRK")
        every { canonical.ensureArtist(any(), any(), any(), any()) } returns Artist(id = 10, name = "Therion", fingerprint = "fp-a")
        every { canonical.ensureAlbum(any(), any(), any(), any(), any(), any(), any()) } returns
            Album(id = 20, artistId = 10, title = "Alb", year = 2020, coverUrl = null, fingerprint = "fp-b")

        val mbidSlot = slot<String?>()
        every {
            canonical.ensureTrack(
                album = any(),
                title = any(),
                trackNumber = any(),
                discNumber = any(),
                durationMs = any(),
                musicbrainzId = captureNullable(mbidSlot),
                plexGuid = any(),
                spotifyId = any(),
            )
        } returns Track(id = 30, albumId = 20, title = "t", trackNumber = 2, discNumber = 1, durationMs = null, fingerprint = "fp-c")

        every { playbackRepo.save(any()) } answers { firstArg<TrackPlayback>().copy(id = 1) }

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
                    if (withMbid != null) {
                        list + PlexWebhookPayload.PlexMetadata.PlexGuidMetadata("mbid://$withMbid")
                    } else {
                        list
                    }
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

        // Usa seu JacksonConfig
        return JacksonConfig().objectMapper().readValue(json, PlexWebhookPayload::class.java)
    }
}
