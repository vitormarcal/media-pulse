package dev.marcal.mediapulse.server.service.plex.import

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.integration.plex.dto.PlexAlbum
import dev.marcal.mediapulse.server.integration.plex.dto.PlexArtist
import dev.marcal.mediapulse.server.integration.plex.dto.PlexGuid
import dev.marcal.mediapulse.server.integration.plex.dto.PlexLibrarySection
import dev.marcal.mediapulse.server.integration.plex.dto.PlexTag
import dev.marcal.mediapulse.server.integration.plex.dto.PlexTrack
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.model.music.Track
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.service.music.AlbumGenreService
import dev.marcal.mediapulse.server.service.plex.PlexArtworkService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PlexImportServiceTest {
    @MockK lateinit var plexApi: PlexApiClient

    @MockK lateinit var canonical: CanonicalizationService

    @MockK lateinit var plexArtworkService: PlexArtworkService

    @MockK lateinit var albumGenreService: AlbumGenreService

    private lateinit var service: PlexImportService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            PlexImportService(
                plexApi = plexApi,
                canonical = canonical,
                plexArtworkService = plexArtworkService,
                albumGenreService = albumGenreService,
            )
    }

    @Test
    fun `should import artists and albums with pagination`() =
        runBlocking {
            val sectionKey = "1"
            val artist1 = Artist(id = 100L, name = "Artist 1", fingerprint = "fp1")
            val artist2 = Artist(id = 101L, name = "Artist 2", fingerprint = "fp2")

            val album1 = Album(id = 200L, artistId = 100L, title = "Album 1", titleKey = "album-1", year = 2020, coverUrl = null, fingerprint = "fpalbum1")
            val album2 = Album(id = 201L, artistId = 101L, title = "Album 2", titleKey = "album-2", year = 2021, coverUrl = null, fingerprint = "fpalbum2")

            val track1 = Track(id = 300L, artistId = 100L, title = "Track 1", durationMs = 180000, fingerprint = "fptrack1")
            val track2 = Track(id = 301L, artistId = 101L, title = "Track 2", durationMs = 200000, fingerprint = "fptrack2")

            val plexArtist1 =
                PlexArtist(
                    ratingKey = "ra1",
                    title = "Artist 1",
                    guids = emptyList(),
                )
            val plexArtist2 =
                PlexArtist(
                    ratingKey = "ra2",
                    title = "Artist 2",
                    guids = emptyList(),
                )

            val plexAlbum1 =
                PlexAlbum(
                    ratingKey = "ral1",
                    title = "Album 1",
                    year = 2020,
                    thumb = null,
                    guids = emptyList(),
                    genres = emptyList(),
                )
            val plexAlbum2 =
                PlexAlbum(
                    ratingKey = "ral2",
                    title = "Album 2",
                    year = 2021,
                    thumb = null,
                    guids = emptyList(),
                    genres = emptyList(),
                )

            val plexTrack1 =
                PlexTrack(
                    ratingKey = "rt1",
                    title = "Track 1",
                    duration = 180000,
                    index = 1,
                    parentIndex = 1,
                    guids = emptyList(),
                )
            val plexTrack2 =
                PlexTrack(
                    ratingKey = "rt2",
                    title = "Track 2",
                    duration = 200000,
                    index = 1,
                    parentIndex = 1,
                    guids = emptyList(),
                )

            // Mock API responses
            coEvery { plexApi.listMusicSections() } returns listOf(PlexLibrarySection(key = sectionKey, type = "artist"))
            // First page returns 2 artists (both fit in one page), second page would be empty, so total should indicate we're done
            coEvery { plexApi.listArtistsPaged(sectionKey, 0, 200) } returns Pair(listOf(plexArtist1, plexArtist2), 2)
            // Don't need to mock the second call since the loop exits when start >= total

            // For each artist, we get their albums (one page each)
            coEvery { plexApi.listAlbumsByArtistPaged(sectionKey, "ra1", 0, 200) } returns Pair(listOf(plexAlbum1), 1)
            coEvery { plexApi.listAlbumsByArtistPaged(sectionKey, "ra2", 0, 200) } returns Pair(listOf(plexAlbum2), 1)

            // For each album, we get its tracks (one page each)
            coEvery { plexApi.listTracksByAlbumPaged(sectionKey, "ral1", 0, 200) } returns Pair(listOf(plexTrack1), 1)
            coEvery { plexApi.listTracksByAlbumPaged(sectionKey, "ral2", 0, 200) } returns Pair(listOf(plexTrack2), 1)

            // Mock canonical service responses
            every { canonical.ensureArtist(name = "Artist 1", musicbrainzId = null, spotifyId = null) } returns artist1
            every { canonical.ensureArtist(name = "Artist 2", musicbrainzId = null, spotifyId = null) } returns artist2

            every { canonical.ensureAlbum(artist = artist1, title = "Album 1", year = 2020, coverUrl = null, musicbrainzId = null, spotifyId = null) } returns album1
            every { canonical.ensureAlbum(artist = artist2, title = "Album 2", year = 2021, coverUrl = null, musicbrainzId = null, spotifyId = null) } returns album2

            every { canonical.ensureTrack(artist = artist1, title = "Track 1", durationMs = 180000, musicbrainzId = null, spotifyId = null) } returns track1
            every { canonical.ensureTrack(artist = artist2, title = "Track 2", durationMs = 200000, musicbrainzId = null, spotifyId = null) } returns track2

            every { canonical.linkTrackToAlbum(album = album1, track = track1, discNumber = 1, trackNumber = 1) } just runs
            every { canonical.linkTrackToAlbum(album = album2, track = track2, discNumber = 1, trackNumber = 1) } just runs

            // Mock artwork and genre services
            coEvery { plexArtworkService.ensureAlbumCoverFromPlexThumb(artist = any(), album = any(), plexThumbPath = null) } just runs
            coEvery { albumGenreService.addGenres(album = any(), genres = emptyList(), source = any()) } just runs

            // Execute
            val stats = service.importAllArtistsAndAlbums(sectionKey = sectionKey, pageSize = 200)

            // Verify stats
            assertEquals(2, stats.artistsSeen)
            assertEquals(2, stats.artistsUpserted)
            assertEquals(2, stats.albumsSeen)
            assertEquals(2, stats.albumsUpserted)
            assertEquals(2, stats.tracksSeen)
            assertEquals(2, stats.tracksUpserted)

            // Verify API calls
            coVerify(exactly = 1) { plexApi.listArtistsPaged(sectionKey, 0, 200) }
            coVerify(exactly = 1) { plexApi.listAlbumsByArtistPaged(sectionKey, "ra1", 0, 200) }
            coVerify(exactly = 1) { plexApi.listAlbumsByArtistPaged(sectionKey, "ra2", 0, 200) }
            coVerify(exactly = 1) { plexApi.listTracksByAlbumPaged(sectionKey, "ral1", 0, 200) }
            coVerify(exactly = 1) { plexApi.listTracksByAlbumPaged(sectionKey, "ral2", 0, 200) }

            // Verify canonical calls
            coVerify(exactly = 2) { canonical.ensureArtist(name = any(), musicbrainzId = null, spotifyId = null) }
            coVerify(exactly = 2) { canonical.ensureAlbum(artist = any(), title = any(), year = any(), coverUrl = null, musicbrainzId = null, spotifyId = null) }
            coVerify(exactly = 2) { canonical.ensureTrack(artist = any(), title = any(), durationMs = any(), musicbrainzId = null, spotifyId = null) }
            coVerify(exactly = 2) { canonical.linkTrackToAlbum(album = any(), track = any(), discNumber = any(), trackNumber = any()) }

            // Verify artwork and genre services
            coVerify(exactly = 2) { plexArtworkService.ensureAlbumCoverFromPlexThumb(artist = any(), album = any(), plexThumbPath = null) }
            coVerify(exactly = 2) { albumGenreService.addGenres(album = any(), genres = emptyList(), source = any()) }
        }

    @Test
    fun `should extract MBID from plex guids when available`() =
        runBlocking {
            val sectionKey = "1"
            val artist = Artist(id = 100L, name = "Artist", fingerprint = "fp1")
            val album = Album(id = 200L, artistId = 100L, title = "Album", titleKey = "album", year = 2020, coverUrl = null, fingerprint = "fpalbum")
            val track = Track(id = 300L, artistId = 100L, title = "Track", durationMs = 180000, fingerprint = "fptrack")

            // Create Plex objects with MBID guids
            val plexArtist =
                PlexArtist(
                    ratingKey = "ra1",
                    title = "Artist",
                    guids = listOf(PlexGuid(id = "mbid://artist-mbid-123")),
                )

            val plexAlbum =
                PlexAlbum(
                    ratingKey = "ral1",
                    title = "Album",
                    year = 2020,
                    thumb = null,
                    guids = listOf(PlexGuid(id = "mbid://album-mbid-456")),
                    genres = emptyList(),
                )

            val plexTrack =
                PlexTrack(
                    ratingKey = "rt1",
                    title = "Track",
                    duration = 180000,
                    index = 1,
                    parentIndex = 1,
                    guids = listOf(PlexGuid(id = "mbid://track-mbid-789")),
                )

            // Mock API responses
            coEvery { plexApi.listMusicSections() } returns listOf(PlexLibrarySection(key = sectionKey, type = "artist"))
            coEvery { plexApi.listArtistsPaged(sectionKey, 0, 200) } returns Pair(listOf(plexArtist), 1)
            coEvery { plexApi.listArtistsPaged(sectionKey, 1, 200) } returns Pair(emptyList(), 1)

            coEvery { plexApi.listAlbumsByArtistPaged(sectionKey, "ra1", 0, 200) } returns Pair(listOf(plexAlbum), 1)
            coEvery { plexApi.listAlbumsByArtistPaged(sectionKey, "ra1", 1, 200) } returns Pair(emptyList(), 1)

            coEvery { plexApi.listTracksByAlbumPaged(sectionKey, "ral1", 0, 200) } returns Pair(listOf(plexTrack), 1)
            coEvery { plexApi.listTracksByAlbumPaged(sectionKey, "ral1", 1, 200) } returns Pair(emptyList(), 1)

            // Mock canonical service with MBID verification
            every { canonical.ensureArtist(name = "Artist", musicbrainzId = "artist-mbid-123", spotifyId = null) } returns artist
            every { canonical.ensureAlbum(artist = artist, title = "Album", year = 2020, coverUrl = null, musicbrainzId = "album-mbid-456", spotifyId = null) } returns album
            every { canonical.ensureTrack(artist = artist, title = "Track", durationMs = 180000, musicbrainzId = "track-mbid-789", spotifyId = null) } returns track

            every { canonical.linkTrackToAlbum(album = album, track = track, discNumber = 1, trackNumber = 1) } just runs

            // Mock artwork and genre services
            coEvery { plexArtworkService.ensureAlbumCoverFromPlexThumb(artist = any(), album = any(), plexThumbPath = null) } just runs
            coEvery { albumGenreService.addGenres(album = any(), genres = emptyList(), source = any()) } just runs

            // Execute
            val stats = service.importAllArtistsAndAlbums(sectionKey = sectionKey, pageSize = 200)

            // Verify stats
            assertEquals(1, stats.artistsSeen)
            assertEquals(1, stats.artistsUpserted)
            assertEquals(1, stats.albumsSeen)
            assertEquals(1, stats.albumsUpserted)
            assertEquals(1, stats.tracksSeen)
            assertEquals(1, stats.tracksUpserted)

            // Verify that MBID values were extracted and passed correctly
            coVerify(exactly = 1) { canonical.ensureArtist(name = "Artist", musicbrainzId = "artist-mbid-123", spotifyId = null) }
            coVerify(exactly = 1) { canonical.ensureAlbum(artist = artist, title = "Album", year = 2020, coverUrl = null, musicbrainzId = "album-mbid-456", spotifyId = null) }
            coVerify(exactly = 1) { canonical.ensureTrack(artist = artist, title = "Track", durationMs = 180000, musicbrainzId = "track-mbid-789", spotifyId = null) }
        }

    @Test
    fun `should handle albums with genres`() =
        runBlocking {
            val sectionKey = "1"
            val artist = Artist(id = 100L, name = "Artist", fingerprint = "fp1")
            val album = Album(id = 200L, artistId = 100L, title = "Album", titleKey = "album", year = 2020, coverUrl = null, fingerprint = "fpalbum")
            val track = Track(id = 300L, artistId = 100L, title = "Track", durationMs = 180000, fingerprint = "fptrack")

            val plexArtist =
                PlexArtist(
                    ratingKey = "ra1",
                    title = "Artist",
                    guids = emptyList(),
                )

            val plexAlbum =
                PlexAlbum(
                    ratingKey = "ral1",
                    title = "Album",
                    year = 2020,
                    thumb = null,
                    guids = emptyList(),
                    genres = listOf(PlexTag(tag = "Rock"), PlexTag(tag = "Pop")),
                )

            val plexTrack =
                PlexTrack(
                    ratingKey = "rt1",
                    title = "Track",
                    duration = 180000,
                    index = 1,
                    parentIndex = 1,
                    guids = emptyList(),
                )

            // Mock API responses
            coEvery { plexApi.listMusicSections() } returns listOf(PlexLibrarySection(key = sectionKey, type = "artist"))
            coEvery { plexApi.listArtistsPaged(sectionKey, 0, 200) } returns Pair(listOf(plexArtist), 1)
            coEvery { plexApi.listArtistsPaged(sectionKey, 1, 200) } returns Pair(emptyList(), 1)

            coEvery { plexApi.listAlbumsByArtistPaged(sectionKey, "ra1", 0, 200) } returns Pair(listOf(plexAlbum), 1)
            coEvery { plexApi.listAlbumsByArtistPaged(sectionKey, "ra1", 1, 200) } returns Pair(emptyList(), 1)

            coEvery { plexApi.listTracksByAlbumPaged(sectionKey, "ral1", 0, 200) } returns Pair(listOf(plexTrack), 1)
            coEvery { plexApi.listTracksByAlbumPaged(sectionKey, "ral1", 1, 200) } returns Pair(emptyList(), 1)

            // Mock canonical service
            every { canonical.ensureArtist(name = "Artist", musicbrainzId = null, spotifyId = null) } returns artist
            every { canonical.ensureAlbum(artist = artist, title = "Album", year = 2020, coverUrl = null, musicbrainzId = null, spotifyId = null) } returns album
            every { canonical.ensureTrack(artist = artist, title = "Track", durationMs = 180000, musicbrainzId = null, spotifyId = null) } returns track

            every { canonical.linkTrackToAlbum(album = album, track = track, discNumber = 1, trackNumber = 1) } just runs

            // Mock artwork and genre services
            coEvery { plexArtworkService.ensureAlbumCoverFromPlexThumb(artist = any(), album = any(), plexThumbPath = null) } just runs
            coEvery { albumGenreService.addGenres(album = album, genres = listOf("Rock", "Pop"), source = any()) } just runs

            // Execute
            val stats = service.importAllArtistsAndAlbums(sectionKey = sectionKey, pageSize = 200)

            // Verify stats
            assertEquals(1, stats.artistsSeen)
            assertEquals(1, stats.artistsUpserted)
            assertEquals(1, stats.albumsSeen)
            assertEquals(1, stats.albumsUpserted)
            assertEquals(1, stats.tracksSeen)
            assertEquals(1, stats.tracksUpserted)

            // Verify that genres were extracted and passed correctly
            coVerify(exactly = 1) { albumGenreService.addGenres(album = album, genres = listOf("Rock", "Pop"), source = any()) }
            coVerify(exactly = 1) { plexArtworkService.ensureAlbumCoverFromPlexThumb(artist = any(), album = any(), plexThumbPath = null) }
        }

    @Test
    fun `should import with specific section key`() =
        runBlocking {
            val sectionKey = "2"
            val artist = Artist(id = 100L, name = "Artist", fingerprint = "fp1")
            val album = Album(id = 200L, artistId = 100L, title = "Album", titleKey = "album", year = 2020, coverUrl = null, fingerprint = "fpalbum")
            val track = Track(id = 300L, artistId = 100L, title = "Track", durationMs = 180000, fingerprint = "fptrack")

            val plexArtist =
                PlexArtist(
                    ratingKey = "ra1",
                    title = "Artist",
                    guids = emptyList(),
                )

            val plexAlbum =
                PlexAlbum(
                    ratingKey = "ral1",
                    title = "Album",
                    year = 2020,
                    thumb = null,
                    guids = emptyList(),
                    genres = emptyList(),
                )

            val plexTrack =
                PlexTrack(
                    ratingKey = "rt1",
                    title = "Track",
                    duration = 180000,
                    index = 1,
                    parentIndex = 1,
                    guids = emptyList(),
                )

            // Mock API responses - should NOT call listMusicSections
            coEvery { plexApi.listArtistsPaged(sectionKey, 0, 200) } returns Pair(listOf(plexArtist), 1)
            coEvery { plexApi.listArtistsPaged(sectionKey, 1, 200) } returns Pair(emptyList(), 1)

            coEvery { plexApi.listAlbumsByArtistPaged(sectionKey, "ra1", 0, 200) } returns Pair(listOf(plexAlbum), 1)
            coEvery { plexApi.listAlbumsByArtistPaged(sectionKey, "ra1", 1, 200) } returns Pair(emptyList(), 1)

            coEvery { plexApi.listTracksByAlbumPaged(sectionKey, "ral1", 0, 200) } returns Pair(listOf(plexTrack), 1)
            coEvery { plexApi.listTracksByAlbumPaged(sectionKey, "ral1", 1, 200) } returns Pair(emptyList(), 1)

            // Mock canonical service
            every { canonical.ensureArtist(name = "Artist", musicbrainzId = null, spotifyId = null) } returns artist
            every { canonical.ensureAlbum(artist = artist, title = "Album", year = 2020, coverUrl = null, musicbrainzId = null, spotifyId = null) } returns album
            every { canonical.ensureTrack(artist = artist, title = "Track", durationMs = 180000, musicbrainzId = null, spotifyId = null) } returns track

            every { canonical.linkTrackToAlbum(album = album, track = track, discNumber = 1, trackNumber = 1) } just runs

            // Mock artwork and genre services
            coEvery { plexArtworkService.ensureAlbumCoverFromPlexThumb(artist = any(), album = any(), plexThumbPath = null) } just runs
            coEvery { albumGenreService.addGenres(album = any(), genres = emptyList(), source = any()) } just runs

            // Execute with specific section key
            val stats = service.importAllArtistsAndAlbums(sectionKey = sectionKey, pageSize = 200)

            // Verify stats
            assertEquals(1, stats.artistsSeen)
            assertEquals(1, stats.artistsUpserted)
            assertEquals(1, stats.albumsSeen)
            assertEquals(1, stats.albumsUpserted)
            assertEquals(1, stats.tracksSeen)
            assertEquals(1, stats.tracksUpserted)

            // Verify listMusicSections was NOT called (because we provided a specific section key)
            coVerify(exactly = 0) { plexApi.listMusicSections() }
        }

    @Test
    fun `should return empty stats when no artists found`() =
        runBlocking {
            val sectionKey = "1"

            // Mock API responses returning empty lists
            coEvery { plexApi.listMusicSections() } returns listOf(PlexLibrarySection(key = sectionKey, type = "artist"))
            coEvery { plexApi.listArtistsPaged(sectionKey, 0, 200) } returns Pair(emptyList(), 0)

            // Execute
            val stats = service.importAllArtistsAndAlbums(sectionKey = sectionKey, pageSize = 200)

            // Verify stats are all zero
            assertEquals(0, stats.artistsSeen)
            assertEquals(0, stats.artistsUpserted)
            assertEquals(0, stats.albumsSeen)
            assertEquals(0, stats.albumsUpserted)
            assertEquals(0, stats.tracksSeen)
            assertEquals(0, stats.tracksUpserted)
        }
}
