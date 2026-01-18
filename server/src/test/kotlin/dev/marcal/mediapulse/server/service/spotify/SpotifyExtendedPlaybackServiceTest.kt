package dev.marcal.mediapulse.server.service.spotify

import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyExtendedHistoryItem
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.model.music.PlaybackSource
import dev.marcal.mediapulse.server.model.music.Track
import dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

class SpotifyExtendedPlaybackServiceTest {
    @MockK lateinit var canonical: CanonicalizationService

    @MockK lateinit var trackPlaybackRepo: TrackPlaybackCrudRepository

    @MockK lateinit var tx: TxUtil

    @MockK lateinit var em: EntityManager

    private lateinit var service: SpotifyExtendedPlaybackService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            SpotifyExtendedPlaybackService(
                canonical = canonical,
                trackPlaybackRepo = trackPlaybackRepo,
                tx = tx,
                em = em,
            )
    }

    @Test
    fun `should process chunk with caching and flush at 100 items`() {
        val artist = Artist(id = 1L, name = "Artist", fingerprint = "fpA")
        val album = Album(id = 2L, artistId = 1L, title = "Album", titleKey = "album", year = 2020, coverUrl = null, fingerprint = "fpB")
        val track = Track(id = 3L, artistId = 1L, title = "Track", durationMs = 180000, fingerprint = "fpT")

        // Create 150 items to test flushing at 100
        val items = (1..150).map { i ->
            SpotifyExtendedHistoryItem(
                ts = "2020-01-01T${String.format("%02d", i % 24)}:${String.format("%02d", (i / 24) % 60)}:00Z",
                trackName = "Track $i",
                artistName = "Artist",
                albumName = "Album",
                spotifyTrackUri = "spotify:track:track$i",
            )
        }

        // Mock transaction execution
        val txLambda = slot<() -> Unit>()
        every { tx.inTx(capture(txLambda)) } answers {
            txLambda.captured.invoke()
        }

        // Mock canonical service
        every { canonical.ensureArtist(name = "Artist", musicbrainzId = null, spotifyId = null) } returns artist
        every { canonical.ensureAlbum(artist = artist, title = "Album", year = null, coverUrl = null, musicbrainzId = null, spotifyId = null) } returns album
        every { canonical.ensureTrack(artist = artist, title = any(), durationMs = null, musicbrainzId = null, spotifyId = any()) } returns track
        every { canonical.linkTrackToAlbum(album = album, track = track, discNumber = null, trackNumber = null) } just runs

        // Mock playback repo
        every { trackPlaybackRepo.insertIgnore(any(), any(), any(), any(), any()) } just runs

        // Mock entity manager
        every { em.flush() } just runs
        every { em.clear() } just runs

        // Execute
        service.processChunk(items, eventId = 123L)

        // Verify flush was called (at item 100 and at end)
        verify(atLeast = 2) { em.flush() }
        verify(atLeast = 2) { em.clear() }

        // Verify canonical service was called
        verify(atLeast = 1) { canonical.ensureArtist(name = "Artist", musicbrainzId = null, spotifyId = null) }
        verify(atLeast = 1) { canonical.ensureAlbum(artist = artist, title = "Album", year = null, coverUrl = null, musicbrainzId = null, spotifyId = null) }
        verify(exactly = 150) { canonical.ensureTrack(artist = artist, title = any(), durationMs = null, musicbrainzId = null, spotifyId = any()) }
        verify(exactly = 150) { canonical.linkTrackToAlbum(album = album, track = track, discNumber = null, trackNumber = null) }

        // Verify playback inserts
        verify(exactly = 150) { trackPlaybackRepo.insertIgnore(any(), any(), any(), any(), any()) }
    }

    @Test
    fun `should extract track id from spotify uri`() {
        val artist = Artist(id = 1L, name = "Artist", fingerprint = "fpA")
        val album = Album(id = 2L, artistId = 1L, title = "Album", titleKey = "album", year = 2020, coverUrl = null, fingerprint = "fpB")
        val track = Track(id = 3L, artistId = 1L, title = "Track", durationMs = 180000, fingerprint = "fpT")

        val item =
            SpotifyExtendedHistoryItem(
                ts = "2020-01-01T00:00:00Z",
                trackName = "Track Name",
                artistName = "Artist",
                albumName = "Album",
                spotifyTrackUri = "spotify:track:123abc456def",
            )

        val txLambda = slot<() -> Unit>()
        every { tx.inTx(capture(txLambda)) } answers { txLambda.captured.invoke() }

        every { canonical.ensureArtist(name = "Artist", musicbrainzId = null, spotifyId = null) } returns artist
        every { canonical.ensureAlbum(artist = artist, title = "Album", year = null, coverUrl = null, musicbrainzId = null, spotifyId = null) } returns album
        every { canonical.ensureTrack(artist = artist, title = "Track Name", durationMs = null, musicbrainzId = null, spotifyId = "123abc456def") } returns track
        every { canonical.linkTrackToAlbum(album = album, track = track, discNumber = null, trackNumber = null) } just runs
        every { trackPlaybackRepo.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { em.flush() } just runs
        every { em.clear() } just runs

        service.processChunk(listOf(item), eventId = 123L)

        verify(exactly = 1) { canonical.ensureTrack(artist = artist, title = "Track Name", durationMs = null, musicbrainzId = null, spotifyId = "123abc456def") }
    }

    @Test
    fun `should skip item when timestamp is missing or invalid`() {
        val txLambda = slot<() -> Unit>()
        every { tx.inTx(capture(txLambda)) } answers { txLambda.captured.invoke() }
        every { em.flush() } just runs
        every { em.clear() } just runs

        val items =
            listOf(
                SpotifyExtendedHistoryItem(ts = null, trackName = "Track", artistName = "Artist", albumName = "Album", spotifyTrackUri = null),
                SpotifyExtendedHistoryItem(ts = "invalid-date", trackName = "Track", artistName = "Artist", albumName = "Album", spotifyTrackUri = null),
            )

        service.processChunk(items, eventId = 123L)

        // Verify no canonical service calls were made (items were skipped)
        verify(exactly = 0) { canonical.ensureArtist(any(), any(), any()) }
    }

    @Test
    fun `should skip item when track name is missing`() {
        val txLambda = slot<() -> Unit>()
        every { tx.inTx(capture(txLambda)) } answers { txLambda.captured.invoke() }
        every { em.flush() } just runs
        every { em.clear() } just runs

        val items =
            listOf(
                SpotifyExtendedHistoryItem(ts = "2020-01-01T00:00:00Z", trackName = null, artistName = "Artist", albumName = "Album", spotifyTrackUri = null),
                SpotifyExtendedHistoryItem(ts = "2020-01-01T00:00:00Z", trackName = "  ", artistName = "Artist", albumName = "Album", spotifyTrackUri = null),
            )

        service.processChunk(items, eventId = 123L)

        verify(exactly = 0) { canonical.ensureArtist(any(), any(), any()) }
    }

    @Test
    fun `should use Unknown for missing artist and album names`() {
        val artist = Artist(id = 1L, name = "Unknown", fingerprint = "fpA")
        val album = Album(id = 2L, artistId = 1L, title = "Unknown", titleKey = "unknown", year = 2020, coverUrl = null, fingerprint = "fpB")
        val track = Track(id = 3L, artistId = 1L, title = "Track", durationMs = 180000, fingerprint = "fpT")

        val item =
            SpotifyExtendedHistoryItem(
                ts = "2020-01-01T00:00:00Z",
                trackName = "Track",
                artistName = null,
                albumName = null,
                spotifyTrackUri = null,
            )

        val txLambda = slot<() -> Unit>()
        every { tx.inTx(capture(txLambda)) } answers { txLambda.captured.invoke() }

        every { canonical.ensureArtist(name = "Unknown", musicbrainzId = null, spotifyId = null) } returns artist
        every { canonical.ensureAlbum(artist = artist, title = "Unknown", year = null, coverUrl = null, musicbrainzId = null, spotifyId = null) } returns album
        every { canonical.ensureTrack(artist = artist, title = "Track", durationMs = null, musicbrainzId = null, spotifyId = null) } returns track
        every { canonical.linkTrackToAlbum(album = album, track = track, discNumber = null, trackNumber = null) } just runs
        every { trackPlaybackRepo.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { em.flush() } just runs
        every { em.clear() } just runs

        service.processChunk(listOf(item), eventId = 123L)

        verify(exactly = 1) { canonical.ensureArtist(name = "Unknown", musicbrainzId = null, spotifyId = null) }
        verify(exactly = 1) { canonical.ensureAlbum(artist = artist, title = "Unknown", year = null, coverUrl = null, musicbrainzId = null, spotifyId = null) }
    }

    @Test
    fun `should handle duplicate playback gracefully with insertIgnore`() {
        val artist = Artist(id = 1L, name = "Artist", fingerprint = "fpA")
        val album = Album(id = 2L, artistId = 1L, title = "Album", titleKey = "album", year = 2020, coverUrl = null, fingerprint = "fpB")
        val track = Track(id = 3L, artistId = 1L, title = "Track", durationMs = 180000, fingerprint = "fpT")

        val item =
            SpotifyExtendedHistoryItem(
                ts = "2020-01-01T00:00:00Z",
                trackName = "Track",
                artistName = "Artist",
                albumName = "Album",
                spotifyTrackUri = "spotify:track:track123",
            )

        val txLambda = slot<() -> Unit>()
        every { tx.inTx(capture(txLambda)) } answers { txLambda.captured.invoke() }

        every { canonical.ensureArtist(name = "Artist", musicbrainzId = null, spotifyId = null) } returns artist
        every { canonical.ensureAlbum(artist = artist, title = "Album", year = null, coverUrl = null, musicbrainzId = null, spotifyId = null) } returns album
        every { canonical.ensureTrack(artist = artist, title = "Track", durationMs = null, musicbrainzId = null, spotifyId = "track123") } returns track
        every { canonical.linkTrackToAlbum(album = album, track = track, discNumber = null, trackNumber = null) } just runs

        // insertIgnore might fail or succeed - should not affect processing
        every { trackPlaybackRepo.insertIgnore(any(), any(), any(), any(), any()) } just runs

        every { em.flush() } just runs
        every { em.clear() } just runs

        // Should not throw exception even if insertIgnore fails
        service.processChunk(listOf(item), eventId = 123L)

        verify(exactly = 1) { trackPlaybackRepo.insertIgnore(trackId = 3L, albumId = 2L, source = PlaybackSource.SPOTIFY.name, sourceEventId = 123L, playedAt = any()) }
    }

    @Test
    fun `should use cache for multiple items with same artist and album`() {
        val artist = Artist(id = 1L, name = "Artist", fingerprint = "fpA")
        val album = Album(id = 2L, artistId = 1L, title = "Album", titleKey = "album", year = 2020, coverUrl = null, fingerprint = "fpB")
        val track1 = Track(id = 3L, artistId = 1L, title = "Track 1", durationMs = 180000, fingerprint = "fpT1")
        val track2 = Track(id = 4L, artistId = 1L, title = "Track 2", durationMs = 200000, fingerprint = "fpT2")

        val items =
            listOf(
                SpotifyExtendedHistoryItem(ts = "2020-01-01T00:00:00Z", trackName = "Track 1", artistName = "Artist", albumName = "Album", spotifyTrackUri = "spotify:track:track1"),
                SpotifyExtendedHistoryItem(ts = "2020-01-01T00:01:00Z", trackName = "Track 2", artistName = "Artist", albumName = "Album", spotifyTrackUri = "spotify:track:track2"),
            )

        val txLambda = slot<() -> Unit>()
        every { tx.inTx(capture(txLambda)) } answers { txLambda.captured.invoke() }

        every { canonical.ensureArtist(name = "Artist", musicbrainzId = null, spotifyId = null) } returns artist
        every { canonical.ensureAlbum(artist = artist, title = "Album", year = null, coverUrl = null, musicbrainzId = null, spotifyId = null) } returns album
        every { canonical.ensureTrack(artist = artist, title = "Track 1", durationMs = null, musicbrainzId = null, spotifyId = "track1") } returns track1
        every { canonical.ensureTrack(artist = artist, title = "Track 2", durationMs = null, musicbrainzId = null, spotifyId = "track2") } returns track2
        every { canonical.linkTrackToAlbum(album = album, track = any(), discNumber = null, trackNumber = null) } just runs
        every { trackPlaybackRepo.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { em.flush() } just runs
        every { em.clear() } just runs

        service.processChunk(items, eventId = 123L)

        // Artist and album should be created only once due to caching
        verify(exactly = 1) { canonical.ensureArtist(name = "Artist", musicbrainzId = null, spotifyId = null) }
        verify(exactly = 1) { canonical.ensureAlbum(artist = artist, title = "Album", year = null, coverUrl = null, musicbrainzId = null, spotifyId = null) }

        // Tracks should be created for each item
        verify(exactly = 1) { canonical.ensureTrack(artist = artist, title = "Track 1", durationMs = null, musicbrainzId = null, spotifyId = "track1") }
        verify(exactly = 1) { canonical.ensureTrack(artist = artist, title = "Track 2", durationMs = null, musicbrainzId = null, spotifyId = "track2") }
    }
}
