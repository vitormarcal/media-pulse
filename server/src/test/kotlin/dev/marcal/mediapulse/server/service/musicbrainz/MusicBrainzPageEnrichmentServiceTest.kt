package dev.marcal.mediapulse.server.service.musicbrainz

import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistCredit
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistRef
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupCandidate
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupResponse
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
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
    private val externalIds = mockk<ExternalIdentifierRepository>()
    private val terms = mockk<AlbumTermsService>()
    private val service = MusicBrainzPageEnrichmentService(client, albums, artists, externalIds, terms)
    private val album = Album(id = 10, artistId = 20, title = "Album", titleKey = "album", fingerprint = "album:20")
    private val artist = Artist(id = 20, name = "Artist", fingerprint = "artist")
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
            verify(exactly = 0) { externalIds.save(any()) }
            verify(exactly = 0) { albums.promoteNullYear(any(), any()) }
        }

    @Test
    fun `apply stores typed links fills missing year and adds sourced terms`() =
        runBlocking {
            every { albums.findById(10) } returns Optional.of(album)
            every { artists.findById(20) } returns Optional.of(artist)
            coEvery { client.getReleaseGroup("rg-1") } returns candidate
            every { externalIds.findByEntityTypeAndEntityIdAndProviderAndExternalEntityType(any(), any(), any(), any()) } returns null
            every { externalIds.findByProviderAndExternalId(any(), any()) } returns null
            every { externalIds.findByEntityTypeAndEntityIdAndProviderAndExternalEntityTypeIsNull(any(), any(), any()) } returns null
            every { externalIds.save(any()) } answers { firstArg<ExternalIdentifier>() }
            every { albums.promoteNullYear(10, 2001) } returns 1
            every { terms.addMusicBrainzTerms(album, listOf("rock"), listOf("indie")) } just runs

            val result = service.applyAlbum(10, "rg-1")

            assertEquals(true, result.yearAdded)
            assertEquals("rg-1", result.releaseGroupMbid)
            verify(exactly = 2) { externalIds.save(any()) }
            verify(exactly = 1) { terms.addMusicBrainzTerms(album, listOf("rock"), listOf("indie")) }
        }

    @Test
    fun `reapplying the same artist link is idempotent`() {
        every { artists.findById(20) } returns Optional.of(artist)
        every { externalIds.findByEntityTypeAndEntityIdAndProviderAndExternalEntityType(any(), any(), any(), any()) } returns
            ExternalIdentifier(
                id = 1,
                entityType = dev.marcal.mediapulse.server.model.EntityType.ARTIST,
                entityId = 20,
                provider = dev.marcal.mediapulse.server.model.Provider.MUSICBRAINZ,
                externalEntityType = dev.marcal.mediapulse.server.model.ExternalEntityType.ARTIST,
                externalId = "artist-1",
            )

        service.applyArtist(20, "artist-1")

        verify(exactly = 0) { externalIds.save(any()) }
    }
}
