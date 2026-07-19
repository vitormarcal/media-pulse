package dev.marcal.mediapulse.server.service.musicbrainz

import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistCandidate
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistCredit
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistRef
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupCandidate
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupResponse
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.AlbumMusicBrainzReleaseId
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.repository.crud.AlbumMusicBrainzReleaseIdRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.service.music.AlbumTermsService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals

class MusicBrainzPageEnrichmentServiceTest {
    private val client = mockk<MusicBrainzApiClient>()
    private val albums = mockk<AlbumRepository>()
    private val artists = mockk<ArtistRepository>()
    private val releaseIds = mockk<AlbumMusicBrainzReleaseIdRepository>()
    private val terms = mockk<AlbumTermsService>()
    private val service = MusicBrainzPageEnrichmentService(client, albums, artists, releaseIds, terms)
    private val album = Album(id = 10, artistId = 20, title = "Album", titleKey = "album", fingerprint = "album:20")
    private val artist = Artist(id = 20, name = "Artist", fingerprint = "artist", musicbrainzArtistId = "artist-1")
    private val candidate =
        MbReleaseGroupCandidate(
            id = "rg-1",
            title = "Album",
            firstReleaseDate = "2001-02-03",
            primaryType = "Album",
            artistCredit = listOf(MbArtistCredit(name = "Artist", artist = MbArtistRef("artist-1", "Artist"))),
            genres = listOf(MbReleaseGroupResponse.MbGenre("rock")),
            tags = listOf(MbReleaseGroupResponse.MbTag("indie")),
        )

    @Test
    fun `preview is read only and reports preserved fields`() =
        runBlocking {
            every { albums.findById(10) } returns Optional.of(album)
            coEvery { client.getReleaseGroup("rg-1") } returns candidate

            val result = service.previewAlbum(10, "rg-1")

            assertEquals(listOf("title", "artist", "coverUrl", "tracklist"), result.preservedFields)
            assertEquals(2001, result.candidate.firstReleaseYear)
            verify(exactly = 0) { albums.save(any()) }
            verify(exactly = 0) { albums.promoteNullYear(any(), any()) }
        }

    @Test
    fun `apply stores release group as canonical album identity`() =
        runBlocking {
            every { albums.findById(10) } returns Optional.of(album)
            every { albums.findByMusicbrainzReleaseGroupId("rg-1") } returns null
            every { albums.save(any()) } answers { firstArg() }
            every { albums.promoteNullYear(10, 2001) } returns 1
            every { artists.findById(20) } returns Optional.of(artist)
            every { releaseIds.findFirstByAlbumIdOrderByIdAsc(10) } returns null
            coEvery { client.getReleaseGroup("rg-1") } returns candidate
            every { terms.addMusicBrainzTerms(album, listOf("rock"), listOf("indie")) } just runs

            val result = service.applyAlbum(10, "rg-1")

            assertEquals("rg-1", result.releaseGroupMbid)
            verify { albums.save(match { it.musicbrainzReleaseGroupId == "rg-1" }) }
        }

    @Test
    fun `reconcile resolves a release alias to canonical release group`() =
        runBlocking {
            every { releaseIds.findFirstByAlbumIdOrderByIdAsc(10) } returns
                AlbumMusicBrainzReleaseId(albumId = 10, releaseId = "release-1")
            every { albums.findById(10) } returns Optional.of(album)
            every { albums.findByMusicbrainzReleaseGroupId("rg-1") } returns null
            every { albums.save(any()) } answers { firstArg() }
            coEvery { client.resolveReleaseGroupFromRelease("release-1") } returns "rg-1"

            service.reconcileReleaseGroup(10)

            verify { albums.save(match { it.musicbrainzReleaseGroupId == "rg-1" }) }
        }

    @Test
    fun `reapplying the same artist link is idempotent`() {
        every { artists.findById(20) } returns Optional.of(artist)

        service.applyArtist(20, "artist-1")

        verify(exactly = 0) { artists.save(any()) }
    }

    @Test
    fun `creating an artist reuses canonical name match and stores identity`() =
        runBlocking {
            val artistWithoutMbid = artist.copy(musicbrainzArtistId = null)
            coEvery { client.getArtist("artist-1") } returns MbArtistCandidate("artist-1", "Artist")
            every { artists.findByMusicbrainzArtistId("artist-1") } returns null
            every { artists.findByFingerprint(any()) } returns artistWithoutMbid
            every { artists.findById(20) } returns Optional.of(artistWithoutMbid)
            every { artists.save(any()) } answers { firstArg() }

            val result = service.createArtist("artist-1")

            assertEquals(20, result.artistId)
            assertEquals(false, result.created)
            verify(exactly = 1) { artists.save(match { it.musicbrainzArtistId == "artist-1" }) }
        }

    @Test
    fun `discography separates possible matches from missing albums`() =
        runBlocking {
            val missing = candidate.copy(id = "rg-2", title = "Another Album", firstReleaseDate = "2005")
            every { artists.findById(20) } returns Optional.of(artist)
            every { albums.findAllByArtistId(20) } returns listOf(album.copy(year = 2001))
            every { albums.findByMusicbrainzReleaseGroupId("rg-1") } returns null
            every { albums.findByMusicbrainzReleaseGroupId("rg-2") } returns null
            coEvery { client.getArtistReleaseGroups("artist-1") } returns listOf(candidate, missing)

            val result = service.getArtistDiscography(20)

            assertEquals(dev.marcal.mediapulse.server.api.music.MusicBrainzDiscographyStatus.POSSIBLE_MATCH, result.items[0].status)
            assertEquals(dev.marcal.mediapulse.server.api.music.MusicBrainzDiscographyStatus.MISSING, result.items[1].status)
            assertEquals(1, result.creatableCount)
        }

    @Test
    fun `discography import skips an already linked release group`() =
        runBlocking {
            every { artists.findById(20) } returns Optional.of(artist)
            every { albums.findAllByArtistId(20) } returns listOf(album)
            every { albums.findByMusicbrainzReleaseGroupId("rg-1") } returns album.copy(musicbrainzReleaseGroupId = "rg-1")
            coEvery { client.getArtistReleaseGroups("artist-1") } returns listOf(candidate)

            val result = service.importArtistDiscography(20, listOf("rg-1"))

            assertEquals(emptyList(), result.createdAlbumIds)
            assertEquals(listOf("rg-1"), result.skippedReleaseGroupMbids)
            verify(exactly = 0) { albums.save(any()) }
        }
}
