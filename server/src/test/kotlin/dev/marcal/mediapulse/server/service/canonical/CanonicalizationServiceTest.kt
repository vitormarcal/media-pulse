package dev.marcal.mediapulse.server.service.canonical

import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.AlbumTrack
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.model.music.Track
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumTrackCrudRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.TrackRepository
import io.mockk.MockKAnnotations
import io.mockk.every
import io.mockk.impl.annotations.MockK
import io.mockk.just
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.dao.DataIntegrityViolationException
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class CanonicalizationServiceTest {
    @MockK
    lateinit var artistRepo: ArtistRepository

    @MockK
    lateinit var albumRepo: AlbumRepository

    @MockK
    lateinit var trackRepo: TrackRepository

    @MockK
    lateinit var extRepo: ExternalIdentifierRepository

    @MockK
    lateinit var albumTrackRepo: AlbumTrackCrudRepository

    private lateinit var service: CanonicalizationService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        service =
            CanonicalizationService(
                artistRepo = artistRepo,
                albumRepo = albumRepo,
                trackRepo = trackRepo,
                extRepo = extRepo,
                albumTrackRepo = albumTrackRepo,
            )
    }

    // ==================== ensureArtist Tests ====================

    @Test
    fun `should find existing artist by musicbrainz id`() {
        val existingArtist = Artist(id = 100L, name = "Existing Artist", fingerprint = "fp123")
        val externalId =
            ExternalIdentifier(
                entityType = EntityType.ARTIST,
                entityId = 100L,
                provider = Provider.MUSICBRAINZ,
                externalId = "mb-id-123",
            )

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, "mb-id-123") } returns externalId
        every { artistRepo.findById(100L) } returns java.util.Optional.of(existingArtist)

        val result = service.ensureArtist(name = "New Artist", musicbrainzId = "mb-id-123")

        assertEquals(existingArtist.id, result.id)
        assertEquals("Existing Artist", result.name)
        verify(exactly = 0) { artistRepo.save(any()) }
    }

    @Test
    fun `should find existing artist by spotify id when musicbrainz id not provided`() {
        val existingArtist = Artist(id = 101L, name = "Spotify Artist", fingerprint = "fp456")
        val externalId =
            ExternalIdentifier(
                entityType = EntityType.ARTIST,
                entityId = 101L,
                provider = Provider.SPOTIFY,
                externalId = "spotify-id-456",
            )

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, any()) } returns null
        every { extRepo.findByProviderAndExternalId(Provider.SPOTIFY, "spotify-id-456") } returns externalId
        every { artistRepo.findById(101L) } returns java.util.Optional.of(existingArtist)

        val result = service.ensureArtist(name = "Spotify Artist", spotifyId = "spotify-id-456")

        assertEquals(existingArtist.id, result.id)
        verify(exactly = 0) { artistRepo.save(any()) }
    }

    @Test
    fun `should find existing artist by fingerprint when no external ids provided`() {
        val existingArtist = Artist(id = 102L, name = "Fingerprint Artist", fingerprint = "fpprint")

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, any()) } returns null
        every { artistRepo.findByFingerprint(any()) } returns existingArtist

        val result = service.ensureArtist(name = "Fingerprint Artist")

        assertEquals(existingArtist.id, result.id)
        verify(exactly = 0) { artistRepo.save(any()) }
    }

    @Test
    fun `should create new artist when not found`() {
        val newArtist = Artist(id = 103L, name = "New Artist", fingerprint = "fpnew")

        every { extRepo.findByProviderAndExternalId(any(), any()) } returns null
        every { artistRepo.findByFingerprint(any()) } returns null
        every { artistRepo.save(any()) } returns newArtist
        every { extRepo.save(any()) } returns ExternalIdentifier(
            entityType = EntityType.ARTIST,
            entityId = 103L,
            provider = Provider.SPOTIFY,
            externalId = "spotify-123",
        )

        val result = service.ensureArtist(name = "New Artist", spotifyId = "spotify-123")

        assertEquals(newArtist.id, result.id)
        verify(exactly = 1) { artistRepo.save(any()) }
        verify(exactly = 1) { extRepo.save(any()) }
    }

    @Test
    fun `should link artist to multiple external providers`() {
        val artist = Artist(id = 104L, name = "Multi Provider", fingerprint = "fpmulti")

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, "mb-123") } returns null
        every { extRepo.findByProviderAndExternalId(Provider.SPOTIFY, any()) } returns null
        every { artistRepo.findByFingerprint(any()) } returns artist
        every { extRepo.save(any()) } returnsMany listOf(
            ExternalIdentifier(entityType = EntityType.ARTIST, entityId = 104L, provider = Provider.MUSICBRAINZ, externalId = "mb-123"),
            ExternalIdentifier(entityType = EntityType.ARTIST, entityId = 104L, provider = Provider.SPOTIFY, externalId = "sp-123"),
        )

        val result = service.ensureArtist(name = "Multi Provider", musicbrainzId = "mb-123", spotifyId = "sp-123")

        assertEquals(artist.id, result.id)
        verify(exactly = 2) { extRepo.save(any()) }
    }

    // ==================== ensureAlbum Tests ====================

    @Test
    fun `should find existing album by musicbrainz id`() {
        val artist = Artist(id = 200L, name = "Artist", fingerprint = "fp200")
        val existingAlbum =
            Album(
                id = 210L,
                artistId = 200L,
                title = "Album",
                titleKey = "album",
                year = 2020,
                coverUrl = "url",
                fingerprint = "fpalbum",
            )
        val externalId =
            ExternalIdentifier(
                entityType = EntityType.ALBUM,
                entityId = 210L,
                provider = Provider.MUSICBRAINZ,
                externalId = "mb-album-123",
            )

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, "mb-album-123") } returns externalId
        every { albumRepo.findById(210L) } returns java.util.Optional.of(existingAlbum)

        val result = service.ensureAlbum(
            artist = artist,
            title = "New Album",
            year = 2021,
            coverUrl = null,
            musicbrainzId = "mb-album-123",
        )

        assertEquals(existingAlbum.id, result.id)
        verify(exactly = 0) { albumRepo.save(any()) }
    }

    @Test
    fun `should find existing album by exact year when external ids not found`() {
        val artist = Artist(id = 201L, name = "Artist", fingerprint = "fp201")
        val existingAlbum =
            Album(
                id = 211L,
                artistId = 201L,
                title = "Album",
                titleKey = "album",
                year = 2020,
                coverUrl = null,
                fingerprint = "fpalbum",
            )

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, any()) } returns null
        every { extRepo.findByProviderAndExternalId(Provider.SPOTIFY, any()) } returns null
        every { albumRepo.findByArtistIdAndTitleKeyAndYear(201L, "album", 2020) } returns existingAlbum

        val result = service.ensureAlbum(
            artist = artist,
            title = "Album",
            year = 2020,
            coverUrl = null,
        )

        assertEquals(existingAlbum.id, result.id)
        verify(exactly = 0) { albumRepo.save(any()) }
    }

    @Test
    fun `should pick best album when year is null using null year policy`() {
        val artist = Artist(id = 202L, name = "Artist", fingerprint = "fp202")
        val albumWithYear =
            Album(
                id = 212L,
                artistId = 202L,
                title = "Album",
                titleKey = "album",
                year = 1990,
                coverUrl = null,
                fingerprint = "fpalbum",
            )

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, any()) } returns null
        every { extRepo.findByProviderAndExternalId(Provider.SPOTIFY, any()) } returns null
        every {
            albumRepo.findFirstByArtistIdAndTitleKeyAndYearIsNotNullOrderByYearAscIdAsc(202L, "album")
        } returns albumWithYear

        val result = service.ensureAlbum(
            artist = artist,
            title = "Album",
            year = null,
            coverUrl = null,
        )

        assertEquals(albumWithYear.id, result.id)
        verify(exactly = 0) { albumRepo.save(any()) }
    }

    @Test
    fun `should create new album when not found`() {
        val artist = Artist(id = 203L, name = "Artist", fingerprint = "fp203")
        val newAlbum =
            Album(
                id = 213L,
                artistId = 203L,
                title = "New Album",
                titleKey = "new-album",
                year = 2023,
                coverUrl = null,
                fingerprint = "fpnewalbum",
            )

        every { extRepo.findByProviderAndExternalId(any(), any()) } returns null
        every { albumRepo.findByArtistIdAndTitleKeyAndYear(any(), any(), any()) } returns null
        every {
            albumRepo.findFirstByArtistIdAndTitleKeyAndYearIsNotNullOrderByYearAscIdAsc(any(), any())
        } returns null
        every { albumRepo.findFirstByArtistIdAndTitleKeyAndYearIsNullOrderByIdAsc(any(), any()) } returns null
        every { albumRepo.findByFingerprint(any()) } returns null
        every { albumRepo.save(any()) } returns newAlbum

        val result = service.ensureAlbum(
            artist = artist,
            title = "New Album",
            year = 2023,
            coverUrl = null,
        )

        assertEquals(newAlbum.id, result.id)
        verify(exactly = 1) { albumRepo.save(any()) }
    }

    @Test
    fun `should promote null year when creating album with year that matches existing`() {
        val artist = Artist(id = 204L, name = "Artist", fingerprint = "fp204")
        val albumWithoutYear =
            Album(
                id = 214L,
                artistId = 204L,
                title = "Album",
                titleKey = "album",
                year = null,
                coverUrl = null,
                fingerprint = "fpalbum",
            )
        val promotedAlbum = albumWithoutYear.copy(id = 214L, year = 2020)

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, any()) } returns null
        every { extRepo.findByProviderAndExternalId(Provider.SPOTIFY, any()) } returns null
        every { albumRepo.findByArtistIdAndTitleKeyAndYear(204L, "album", 2020) } returns null
        every {
            albumRepo.findFirstByArtistIdAndTitleKeyAndYearIsNotNullOrderByYearAscIdAsc(204L, "album")
        } returns null
        every { albumRepo.findByFingerprint(any()) } returns albumWithoutYear
        every { albumRepo.promoteNullYear(214L, 2020) } returns 1
        every { albumRepo.findById(214L) } returns java.util.Optional.of(promotedAlbum)

        val result = service.ensureAlbum(
            artist = artist,
            title = "Album",
            year = 2020,
            coverUrl = null,
        )

        assertEquals(promotedAlbum.id, result.id)
        assertEquals(2020, result.year)
        verify(exactly = 1) { albumRepo.promoteNullYear(214L, 2020) }
    }

    @Test
    fun `should handle race condition during null year promotion with DataIntegrityViolationException`() {
        val artist = Artist(id = 205L, name = "Artist", fingerprint = "fp205")
        val albumWithoutYear =
            Album(
                id = 215L,
                artistId = 205L,
                title = "Album",
                titleKey = "album",
                year = null,
                coverUrl = null,
                fingerprint = "fpalbum",
            )
        val winnerAlbum =
            Album(
                id = 216L,
                artistId = 205L,
                title = "Album",
                titleKey = "album",
                year = 2020,
                coverUrl = null,
                fingerprint = "fpalbum2",
            )

        every { extRepo.findByProviderAndExternalId(any(), any()) } returns null
        every { albumRepo.findByArtistIdAndTitleKeyAndYear(any(), any(), any()) } returns null andThen winnerAlbum
        every {
            albumRepo.findFirstByArtistIdAndTitleKeyAndYearIsNotNullOrderByYearAscIdAsc(any(), any())
        } returns null
        every { albumRepo.findFirstByArtistIdAndTitleKeyAndYearIsNullOrderByIdAsc(any(), any()) } returns null
        every { albumRepo.findByFingerprint(any()) } returns null
        every { albumRepo.save(any()) } returns albumWithoutYear
        every { albumRepo.promoteNullYear(any(), any()) } throws DataIntegrityViolationException("Unique constraint violation")

        val result = service.ensureAlbum(
            artist = artist,
            title = "Album",
            year = 2020,
            coverUrl = null,
        )

        assertEquals(winnerAlbum.id, result.id)
        verify(exactly = 2) { albumRepo.findByArtistIdAndTitleKeyAndYear(any(), any(), any()) }
        verify(exactly = 1) { albumRepo.promoteNullYear(any(), any()) }
    }

    @Test
    fun `should link album to external providers`() {
        val artist = Artist(id = 206L, name = "Artist", fingerprint = "fp206")
        val album =
            Album(
                id = 216L,
                artistId = 206L,
                title = "Album",
                titleKey = "album",
                year = 2020,
                coverUrl = null,
                fingerprint = "fpalbum",
            )

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, any()) } returns null
        every { extRepo.findByProviderAndExternalId(Provider.SPOTIFY, any()) } returns null
        every { albumRepo.findByArtistIdAndTitleKeyAndYear(206L, "album", 2020) } returns album
        every { extRepo.save(any()) } returnsMany listOf(
            ExternalIdentifier(entityType = EntityType.ALBUM, entityId = 216L, provider = Provider.MUSICBRAINZ, externalId = "mb-album-456"),
            ExternalIdentifier(entityType = EntityType.ALBUM, entityId = 216L, provider = Provider.SPOTIFY, externalId = "sp-album-456"),
        )

        service.ensureAlbum(
            artist = artist,
            title = "Album",
            year = 2020,
            coverUrl = null,
            musicbrainzId = "mb-album-456",
            spotifyId = "sp-album-456",
        )

        verify(exactly = 2) { extRepo.save(any()) }
    }

    // ==================== ensureTrack Tests ====================

    @Test
    fun `should find existing track by musicbrainz id`() {
        val artist = Artist(id = 300L, name = "Artist", fingerprint = "fp300")
        val existingTrack = Track(id = 310L, artistId = 300L, title = "Track", durationMs = 180000, fingerprint = "fptrack")
        val externalId =
            ExternalIdentifier(
                entityType = EntityType.TRACK,
                entityId = 310L,
                provider = Provider.MUSICBRAINZ,
                externalId = "mb-track-123",
            )

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, "mb-track-123") } returns externalId
        every { trackRepo.findById(310L) } returns java.util.Optional.of(existingTrack)

        val result = service.ensureTrack(
            artist = artist,
            title = "New Track",
            durationMs = 200000,
            musicbrainzId = "mb-track-123",
        )

        assertEquals(existingTrack.id, result.id)
        verify(exactly = 0) { trackRepo.save(any()) }
    }

    @Test
    fun `should find existing track by spotify id when musicbrainz id not provided`() {
        val artist = Artist(id = 301L, name = "Artist", fingerprint = "fp301")
        val existingTrack = Track(id = 311L, artistId = 301L, title = "Track", durationMs = 180000, fingerprint = "fptrack")
        val externalId =
            ExternalIdentifier(
                entityType = EntityType.TRACK,
                entityId = 311L,
                provider = Provider.SPOTIFY,
                externalId = "spotify-track-456",
            )

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, any()) } returns null
        every { extRepo.findByProviderAndExternalId(Provider.SPOTIFY, "spotify-track-456") } returns externalId
        every { trackRepo.findById(311L) } returns java.util.Optional.of(existingTrack)

        val result = service.ensureTrack(
            artist = artist,
            title = "Track",
            durationMs = 180000,
            spotifyId = "spotify-track-456",
        )

        assertEquals(existingTrack.id, result.id)
        verify(exactly = 0) { trackRepo.save(any()) }
    }

    @Test
    fun `should find existing track by fingerprint when no external ids provided`() {
        val artist = Artist(id = 302L, name = "Artist", fingerprint = "fp302")
        val existingTrack = Track(id = 312L, artistId = 302L, title = "Track", durationMs = 180000, fingerprint = "fptrack")

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, any()) } returns null
        every { extRepo.findByProviderAndExternalId(Provider.SPOTIFY, any()) } returns null
        every { trackRepo.findByFingerprint(any()) } returns existingTrack

        val result = service.ensureTrack(
            artist = artist,
            title = "Track",
            durationMs = 180000,
        )

        assertEquals(existingTrack.id, result.id)
        verify(exactly = 0) { trackRepo.save(any()) }
    }

    @Test
    fun `should create new track when not found`() {
        val artist = Artist(id = 303L, name = "Artist", fingerprint = "fp303")
        val newTrack = Track(id = 313L, artistId = 303L, title = "New Track", durationMs = 240000, fingerprint = "fpnewtrack")

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, any()) } returns null
        every { extRepo.findByProviderAndExternalId(Provider.SPOTIFY, any()) } returns null
        every { trackRepo.findByFingerprint(any()) } returns null
        every { trackRepo.save(any()) } returns newTrack
        every { extRepo.save(any()) } returns ExternalIdentifier(
            entityType = EntityType.TRACK,
            entityId = 313L,
            provider = Provider.SPOTIFY,
            externalId = "spotify-123",
        )

        val result = service.ensureTrack(
            artist = artist,
            title = "New Track",
            durationMs = 240000,
            spotifyId = "spotify-123",
        )

        assertEquals(newTrack.id, result.id)
        verify(exactly = 1) { trackRepo.save(any()) }
        verify(exactly = 1) { extRepo.save(any()) }
    }

    @Test
    fun `should handle track with null duration`() {
        val artist = Artist(id = 304L, name = "Artist", fingerprint = "fp304")
        val newTrack = Track(id = 314L, artistId = 304L, title = "Unknown Duration", durationMs = null, fingerprint = "fpunknown")

        every { extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, any()) } returns null
        every { extRepo.findByProviderAndExternalId(Provider.SPOTIFY, any()) } returns null
        every { trackRepo.findByFingerprint(any()) } returns null
        every { trackRepo.save(any()) } returns newTrack

        val result = service.ensureTrack(
            artist = artist,
            title = "Unknown Duration",
            durationMs = null,
        )

        assertEquals(newTrack.id, result.id)
        assertNotNull(result)
    }

    // ==================== linkTrackToAlbum Tests ====================

    @Test
    fun `should upsert track in album with position information`() {
        val album =
            Album(
                id = 400L,
                artistId = 500L,
                title = "Album",
                titleKey = "album",
                year = 2020,
                coverUrl = null,
                fingerprint = "fpalbum",
            )
        val track = Track(id = 401L, artistId = 500L, title = "Track", durationMs = 180000, fingerprint = "fptrack")

        every {
            albumTrackRepo.upsertByPosition(
                lockKey = 400L,
                albumId = 400L,
                trackId = 401L,
                discNumber = 1,
                trackNumber = 5,
            )
        } returns 1

        service.linkTrackToAlbum(
            album = album,
            track = track,
            discNumber = 1,
            trackNumber = 5,
        )

        verify(exactly = 1) {
            albumTrackRepo.upsertByPosition(
                lockKey = 400L,
                albumId = 400L,
                trackId = 401L,
                discNumber = 1,
                trackNumber = 5,
            )
        }
    }

    @Test
    fun `should insert ignore track without position information`() {
        val album =
            Album(
                id = 401L,
                artistId = 501L,
                title = "Album",
                titleKey = "album",
                year = 2020,
                coverUrl = null,
                fingerprint = "fpalbum",
            )
        val track = Track(id = 402L, artistId = 501L, title = "Track", durationMs = 180000, fingerprint = "fptrack")

        every {
            albumTrackRepo.insertIgnoreByPk(
                albumId = 401L,
                trackId = 402L,
                discNumber = null,
                trackNumber = null,
            )
        } returns 0

        service.linkTrackToAlbum(
            album = album,
            track = track,
            discNumber = null,
            trackNumber = null,
        )

        verify(exactly = 1) {
            albumTrackRepo.insertIgnoreByPk(
                albumId = 401L,
                trackId = 402L,
                discNumber = null,
                trackNumber = null,
            )
        }
    }

    @Test
    fun `should insert ignore when only one position field is provided`() {
        val album =
            Album(
                id = 402L,
                artistId = 502L,
                title = "Album",
                titleKey = "album",
                year = 2020,
                coverUrl = null,
                fingerprint = "fpalbum",
            )
        val track = Track(id = 403L, artistId = 502L, title = "Track", durationMs = 180000, fingerprint = "fptrack")

        every {
            albumTrackRepo.insertIgnoreByPk(
                albumId = 402L,
                trackId = 403L,
                discNumber = 1,
                trackNumber = null,
            )
        } returns 0

        service.linkTrackToAlbum(
            album = album,
            track = track,
            discNumber = 1,
            trackNumber = null,
        )

        verify(exactly = 1) {
            albumTrackRepo.insertIgnoreByPk(
                albumId = 402L,
                trackId = 403L,
                discNumber = 1,
                trackNumber = null,
            )
        }
    }

    // ==================== updateAlbumCoverIfEmpty Tests ====================

    @Test
    fun `should update album cover when it is null`() {
        val albumWithoutCover =
            Album(
                id = 500L,
                artistId = 600L,
                title = "Album",
                titleKey = "album",
                year = 2020,
                coverUrl = null,
                fingerprint = "fpalbum",
            )
        val updatedAlbum = albumWithoutCover.copy(coverUrl = "/local/path/to/cover.jpg", updatedAt = Instant.now())

        every { albumRepo.findById(500L) } returns java.util.Optional.of(albumWithoutCover)
        every { albumRepo.save(any()) } returns updatedAlbum

        val result = service.updateAlbumCoverIfEmpty(500L, "/local/path/to/cover.jpg")

        assertEquals("/local/path/to/cover.jpg", result.coverUrl)
        verify(exactly = 1) { albumRepo.save(any()) }
    }

    @Test
    fun `should not update album cover when it already exists`() {
        val albumWithCover =
            Album(
                id = 501L,
                artistId = 601L,
                title = "Album",
                titleKey = "album",
                year = 2020,
                coverUrl = "/existing/cover.jpg",
                fingerprint = "fpalbum",
            )

        every { albumRepo.findById(501L) } returns java.util.Optional.of(albumWithCover)

        val result = service.updateAlbumCoverIfEmpty(501L, "/new/cover.jpg")

        assertEquals("/existing/cover.jpg", result.coverUrl)
        verify(exactly = 0) { albumRepo.save(any()) }
    }

    @Test
    fun `should update timestamp when cover is saved`() {
        val albumWithoutCover =
            Album(
                id = 502L,
                artistId = 602L,
                title = "Album",
                titleKey = "album",
                year = 2020,
                coverUrl = null,
                fingerprint = "fpalbum",
                updatedAt = Instant.parse("2020-01-01T00:00:00Z"),
            )
        val beforeUpdate = Instant.now()
        val updatedAlbum = albumWithoutCover.copy(coverUrl = "/new/cover.jpg", updatedAt = Instant.now())
        val afterUpdate = Instant.now()

        every { albumRepo.findById(502L) } returns java.util.Optional.of(albumWithoutCover)
        every { albumRepo.save(any()) } returns updatedAlbum

        val result = service.updateAlbumCoverIfEmpty(502L, "/new/cover.jpg")

        assertEquals("/new/cover.jpg", result.coverUrl)
        assert(result.updatedAt!! >= beforeUpdate)
        assert(result.updatedAt!! <= afterUpdate)
    }
}
