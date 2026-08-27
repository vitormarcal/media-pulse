package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbUrlRelation
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbUrlResource
import dev.marcal.mediapulse.server.integration.wikimedia.WikimediaApiClient
import dev.marcal.mediapulse.server.integration.wikimedia.WikimediaImageMetadata
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import java.util.Optional

class ArtistWikimediaImageServiceTest {
    private val artists = mockk<ArtistRepository>()
    private val wikimedia = mockk<WikimediaApiClient>()
    private val storage = mockk<ImageStorageService>()
    private val service = ArtistWikimediaImageService(artists, wikimedia, storage)
    private val artist = Artist(id = 7, name = "Björk", fingerprint = "bjork")
    private val relations = listOf(MbUrlRelation("wikidata", MbUrlResource("https://www.wikidata.org/wiki/Q4616")))

    @Test
    fun `imports and persists the primary Wikimedia image`() =
        runBlocking {
            val metadata =
                WikimediaImageMetadata(
                    fileName = "Björk 2022.jpg",
                    downloadUrl = "https://upload.wikimedia.org/thumb.jpg",
                    originalUrl = "https://upload.wikimedia.org/original.jpg",
                    descriptionUrl = "https://commons.wikimedia.org/wiki/File:Björk_2022.jpg",
                    mimeType = "image/jpeg",
                    author = "Photographer",
                    license = "CC BY-SA 4.0",
                    licenseUrl = "https://creativecommons.org/licenses/by-sa/4.0/",
                )
            every { artists.findById(7) } returns Optional.of(artist)
            every { artists.save(any()) } answers { firstArg() }
            coEvery { wikimedia.primaryImageFile("Q4616") } returns metadata.fileName
            coEvery { wikimedia.imageMetadata(metadata.fileName) } returns metadata
            coEvery { wikimedia.downloadImage(metadata) } returns ImageContent(byteArrayOf(1, 2, 3), MediaType.IMAGE_JPEG)
            every { storage.saveImageForArtist(any(), "WIKIMEDIA", 7, "Björk") } returns "/covers/wikimedia/artists/7/bjork.jpg"

            service.ensureImage(7, relations)

            verify {
                artists.save(
                    match {
                        it.profileImageUrl == "/covers/wikimedia/artists/7/bjork.jpg" &&
                            it.wikidataEntityId == "Q4616" &&
                            it.wikimediaAuthor == "Photographer" &&
                            it.wikimediaLicense == "CC BY-SA 4.0" &&
                            it.wikimediaImportedAt != null
                    },
                )
            }
        }

    @Test
    fun `preserves an existing local artist image`() =
        runBlocking {
            every { artists.findById(7) } returns Optional.of(artist.copy(profileImageUrl = "/covers/manual/artist.jpg"))

            service.ensureImage(7, relations)

            coVerify(exactly = 0) { wikimedia.primaryImageFile(any()) }
            verify(exactly = 0) { artists.save(any()) }
        }

    @Test
    fun `records missing Wikidata relation without failing`() =
        runBlocking {
            every { artists.findById(7) } returns Optional.of(artist)
            every { artists.save(any()) } answers { firstArg() }

            service.ensureImage(7, emptyList())

            verify { artists.save(match { it.wikimediaAttemptedAt != null && it.wikimediaSyncError != null }) }
        }

    @Test
    fun `records Wikidata entity without a primary image`() =
        runBlocking {
            every { artists.findById(7) } returns Optional.of(artist)
            every { artists.save(any()) } answers { firstArg() }
            coEvery { wikimedia.primaryImageFile("Q4616") } returns null

            service.ensureImage(7, relations)

            verify {
                artists.save(
                    match {
                        it.wikidataEntityId == "Q4616" &&
                            it.profileImageUrl == null &&
                            it.wikimediaAttemptedAt != null &&
                            it.wikimediaSyncError != null
                    },
                )
            }
            coVerify(exactly = 0) { wikimedia.imageMetadata(any()) }
        }

    @Test
    fun `isolates provider failure and preserves the artist`() =
        runBlocking {
            every { artists.findById(7) } returns Optional.of(artist)
            every { artists.save(any()) } answers { firstArg() }
            coEvery { wikimedia.primaryImageFile("Q4616") } throws IllegalStateException("offline")

            service.ensureImage(7, relations)

            verify { artists.save(match { it.profileImageUrl == null && it.wikimediaSyncError != null }) }
        }
}
