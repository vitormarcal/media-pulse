package dev.marcal.mediapulse.server.service.musicbrainz

import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistCandidate
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

    @Test
    fun `creating an artist reuses the canonical name match and stores typed identity`() =
        runBlocking {
            coEvery { client.getArtist("artist-1") } returns MbArtistCandidate("artist-1", "Artist")
            every { externalIds.findByProviderAndExternalId(any(), "artist-1") } returns null
            every { artists.findByFingerprint(any()) } returns artist
            every { externalIds.findByEntityTypeAndEntityIdAndProviderAndExternalEntityType(any(), any(), any(), any()) } returns null
            every { externalIds.save(any()) } answers { firstArg() }

            val result = service.createArtist("artist-1")

            assertEquals(20, result.artistId)
            assertEquals(false, result.created)
            verify(exactly = 0) { artists.save(any()) }
            verify(exactly = 1) { externalIds.save(any()) }
        }

    @Test
    fun `discography separates possible matches from missing albums`() =
        runBlocking {
            val missing = candidate.copy(id = "rg-2", title = "Another Album", firstReleaseDate = "2005")
            every { artists.findById(20) } returns Optional.of(artist)
            every { externalIds.findByEntityTypeAndEntityIdAndProviderAndExternalEntityType(any(), any(), any(), any()) } answers {
                if (firstArg<dev.marcal.mediapulse.server.model.EntityType>() == dev.marcal.mediapulse.server.model.EntityType.ARTIST) {
                    ExternalIdentifier(
                        entityType = dev.marcal.mediapulse.server.model.EntityType.ARTIST,
                        entityId = 20,
                        provider = dev.marcal.mediapulse.server.model.Provider.MUSICBRAINZ,
                        externalEntityType = dev.marcal.mediapulse.server.model.ExternalEntityType.ARTIST,
                        externalId = "artist-1",
                    )
                } else {
                    null
                }
            }
            every { albums.findAllByArtistId(20) } returns listOf(album.copy(year = 2001))
            coEvery { client.getArtistReleaseGroups("artist-1") } returns listOf(candidate, missing)
            every { externalIds.findByProviderAndExternalId(any(), "rg-1") } returns null
            every { externalIds.findByProviderAndExternalId(any(), "rg-2") } returns null

            val result = service.getArtistDiscography(20)

            assertEquals(dev.marcal.mediapulse.server.api.music.MusicBrainzDiscographyStatus.POSSIBLE_MATCH, result.items[0].status)
            assertEquals(dev.marcal.mediapulse.server.api.music.MusicBrainzDiscographyStatus.MISSING, result.items[1].status)
            assertEquals(1, result.creatableCount)
        }

    @Test
    fun `discography import skips an already linked release group`() =
        runBlocking {
            every { artists.findById(20) } returns Optional.of(artist)
            every { externalIds.findByEntityTypeAndEntityIdAndProviderAndExternalEntityType(any(), any(), any(), any()) } answers {
                if (firstArg<dev.marcal.mediapulse.server.model.EntityType>() == dev.marcal.mediapulse.server.model.EntityType.ARTIST) {
                    ExternalIdentifier(
                        entityType = dev.marcal.mediapulse.server.model.EntityType.ARTIST,
                        entityId = 20,
                        provider = dev.marcal.mediapulse.server.model.Provider.MUSICBRAINZ,
                        externalEntityType = dev.marcal.mediapulse.server.model.ExternalEntityType.ARTIST,
                        externalId = "artist-1",
                    )
                } else {
                    null
                }
            }
            coEvery { client.getArtistReleaseGroups("artist-1") } returns listOf(candidate)
            every { albums.findAllByArtistId(20) } returns listOf(album)
            every { externalIds.findByProviderAndExternalId(any(), "rg-1") } returns
                ExternalIdentifier(
                    entityType = dev.marcal.mediapulse.server.model.EntityType.ALBUM,
                    entityId = 10,
                    provider = dev.marcal.mediapulse.server.model.Provider.MUSICBRAINZ,
                    externalEntityType = dev.marcal.mediapulse.server.model.ExternalEntityType.RELEASE_GROUP,
                    externalId = "rg-1",
                )

            val result = service.importArtistDiscography(20, listOf("rg-1"))

            assertEquals(emptyList(), result.createdAlbumIds)
            assertEquals(listOf("rg-1"), result.skippedReleaseGroupMbids)
            verify(exactly = 0) { albums.save(any()) }
        }

    @Test
    fun `discography import creates a missing album with typed link and terms`() =
        runBlocking {
            val imported = album.copy(id = 30, title = "Another Album", titleKey = "another album", year = 2005)
            val missing = candidate.copy(id = "rg-2", title = "Another Album", firstReleaseDate = "2005")
            every { artists.findById(20) } returns Optional.of(artist)
            every { externalIds.findByEntityTypeAndEntityIdAndProviderAndExternalEntityType(any(), any(), any(), any()) } answers {
                if (firstArg<dev.marcal.mediapulse.server.model.EntityType>() == dev.marcal.mediapulse.server.model.EntityType.ARTIST) {
                    ExternalIdentifier(
                        entityType = dev.marcal.mediapulse.server.model.EntityType.ARTIST,
                        entityId = 20,
                        provider = dev.marcal.mediapulse.server.model.Provider.MUSICBRAINZ,
                        externalEntityType = dev.marcal.mediapulse.server.model.ExternalEntityType.ARTIST,
                        externalId = "artist-1",
                    )
                } else {
                    null
                }
            }
            coEvery { client.getArtistReleaseGroups("artist-1") } returns listOf(missing)
            coEvery { client.getReleaseGroup("rg-2") } returns missing
            every { albums.findAllByArtistId(20) } returns emptyList()
            every { externalIds.findByProviderAndExternalId(any(), "rg-2") } returns null
            every { albums.save(any()) } returns imported
            every { externalIds.save(any()) } answers { firstArg() }
            every { terms.addMusicBrainzTerms(imported, listOf("rock"), listOf("indie")) } just runs

            val result = service.importArtistDiscography(20, listOf("rg-2"))

            assertEquals(listOf(30L), result.createdAlbumIds)
            verify(exactly = 1) { externalIds.save(match { it.externalEntityType?.name == "RELEASE_GROUP" }) }
            verify(exactly = 1) { terms.addMusicBrainzTerms(imported, listOf("rock"), listOf("indie")) }
        }
}
