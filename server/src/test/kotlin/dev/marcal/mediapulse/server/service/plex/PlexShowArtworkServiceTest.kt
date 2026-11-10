package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import dev.marcal.mediapulse.server.service.tv.TvShowImagePrimaryService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class PlexShowArtworkServiceTest {
    private lateinit var plexApiClient: PlexApiClient
    private lateinit var imageStorageService: LibraryImageStorageService
    private lateinit var tvShowImageCrudRepository: TvShowImageCrudRepository
    private lateinit var tvShowImagePrimaryService: TvShowImagePrimaryService
    private lateinit var tvShowRepository: TvShowRepository
    private lateinit var service: PlexShowArtworkService

    @BeforeEach
    fun setUp() {
        plexApiClient = mockk(relaxed = true)
        imageStorageService = mockk(relaxed = true)
        tvShowImageCrudRepository = mockk(relaxed = true)
        tvShowRepository = mockk(relaxed = true)
        tvShowImagePrimaryService = TvShowImagePrimaryService(tvShowImageCrudRepository, tvShowRepository)
        every { tvShowImageCrudRepository.lockShowRowForPrimaryUpdate(any()) } answers { firstArg() }

        service =
            PlexShowArtworkService(
                plexApi = plexApiClient,
                imageStorageService = imageStorageService,
                tvShowImagePrimaryService = tvShowImagePrimaryService,
            )
    }

    @Test
    fun `should save all images and set poster as primary`() =
        runBlocking {
            val show = TvShow(id = 10, originalTitle = "Severance", description = null, coverUrl = null, fingerprint = "fp")
            val imageContent = ImageContent("abc".toByteArray(), MediaType.IMAGE_JPEG)

            coEvery { plexApiClient.downloadImageContent(any()) } returns imageContent
            every {
                imageStorageService.saveShow(
                    image = any(),
                    provider = "PLEX",
                    showId = 10,
                    fileHint = any(),
                    title = show.originalTitle,
                )
            } returnsMany
                listOf(
                    "/covers/plex/tv-shows/10/background.jpg",
                    "/covers/plex/tv-shows/10/poster.jpg",
                )

            val images =
                listOf(
                    PlexShowArtworkService.PlexShowImageCandidate(url = "/library/metadata/100/art/1", isPoster = false),
                    PlexShowArtworkService.PlexShowImageCandidate(url = "/library/metadata/100/thumb/1", isPoster = true),
                )

            service.ensureShowImagesFromPlex(show = show, images = images, fallbackThumbPath = null)

            verify(exactly = 2) { tvShowImageCrudRepository.insertIgnore(showId = 10, url = any(), isPrimary = false) }
            verify(exactly = 1) { tvShowImageCrudRepository.markPrimaryForShow(10, "/covers/plex/tv-shows/10/poster.jpg") }
            verify(exactly = 1) { tvShowRepository.updateCover(10, "/covers/plex/tv-shows/10/poster.jpg") }
        }

    @Test
    fun `should use fallback thumb when image list is empty`() =
        runBlocking {
            val show = TvShow(id = 11, originalTitle = "Dark", description = null, coverUrl = null, fingerprint = "fp2")
            val imageContent = ImageContent("abc".toByteArray(), MediaType.IMAGE_JPEG)

            coEvery { plexApiClient.downloadImageContent(any()) } returns imageContent
            every {
                imageStorageService.saveShow(
                    image = any(),
                    provider = "PLEX",
                    showId = 11,
                    fileHint = any(),
                    title = show.originalTitle,
                )
            } returns "/covers/plex/tv-shows/11/cover.jpg"

            service.ensureShowImagesFromPlex(
                show = show,
                images = emptyList(),
                fallbackThumbPath = "/library/metadata/999/thumb/111",
            )

            verify(exactly = 1) { tvShowImageCrudRepository.insertIgnore(11, "/covers/plex/tv-shows/11/cover.jpg", false) }
            verify(exactly = 1) { tvShowImageCrudRepository.markPrimaryForShow(11, "/covers/plex/tv-shows/11/cover.jpg") }
            verify(exactly = 1) { tvShowRepository.updateCover(11, "/covers/plex/tv-shows/11/cover.jpg") }
        }

    @Test
    fun `failed upload does not change gallery or cover`() =
        runBlocking {
            val show = TvShow(id = 12, originalTitle = "Dark", coverUrl = "chosen", fingerprint = "fp3")
            coEvery { plexApiClient.downloadImageContent(any()) } returns ImageContent(byteArrayOf(1), MediaType.IMAGE_JPEG)
            every { imageStorageService.saveShow(any(), 12, "Dark", "PLEX", any()) } throws IllegalStateException("unavailable")

            service.ensureShowImagesFromPlex(show, emptyList(), "/thumb")

            verify(exactly = 0) { tvShowImageCrudRepository.insertIgnore(any(), any(), any()) }
            verify(exactly = 0) { tvShowRepository.updateCover(any(), any()) }
        }
}
