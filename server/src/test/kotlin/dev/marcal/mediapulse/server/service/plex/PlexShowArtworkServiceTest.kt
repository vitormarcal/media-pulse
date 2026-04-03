package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import dev.marcal.mediapulse.server.service.tv.TvShowImagePrimaryService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import kotlin.test.assertEquals

class PlexShowArtworkServiceTest {
    private lateinit var plexApiClient: PlexApiClient
    private lateinit var imageStorageService: ImageStorageService
    private lateinit var tvShowImageCrudRepository: TvShowImageCrudRepository
    private lateinit var tvShowImagePrimaryService: TvShowImagePrimaryService
    private lateinit var tvShowRepository: TvShowRepository
    private lateinit var service: PlexShowArtworkService

    @BeforeEach
    fun setUp() {
        plexApiClient = mockk(relaxed = true)
        imageStorageService = mockk(relaxed = true)
        tvShowImageCrudRepository = mockk(relaxed = true)
        tvShowImagePrimaryService = mockk(relaxed = true)
        tvShowRepository = mockk(relaxed = true)

        service =
            PlexShowArtworkService(
                plexApi = plexApiClient,
                imageStorageService = imageStorageService,
                tvShowImageCrudRepository = tvShowImageCrudRepository,
                tvShowImagePrimaryService = tvShowImagePrimaryService,
                tvShowRepository = tvShowRepository,
            )
    }

    @Test
    fun `should save all images and set poster as primary`() =
        runBlocking {
            val show = TvShow(id = 10, originalTitle = "Severance", description = null, coverUrl = null, fingerprint = "fp")
            val imageContent = ImageContent("abc".toByteArray(), MediaType.IMAGE_JPEG)

            coEvery { plexApiClient.downloadImageContent(any()) } returns imageContent
            every {
                imageStorageService.saveImageForTvShow(
                    image = any(),
                    provider = "PLEX",
                    showId = 10,
                    fileNameHint = any(),
                )
            } returnsMany
                listOf(
                    "/covers/plex/tv-shows/10/background.jpg",
                    "/covers/plex/tv-shows/10/poster.jpg",
                )
            every { tvShowRepository.save(any()) } answers { firstArg() }

            val images =
                listOf(
                    PlexShowArtworkService.PlexShowImageCandidate(url = "/library/metadata/100/art/1", isPoster = false),
                    PlexShowArtworkService.PlexShowImageCandidate(url = "/library/metadata/100/thumb/1", isPoster = true),
                )

            service.ensureShowImagesFromPlex(show = show, images = images, fallbackThumbPath = null)

            verify(exactly = 2) { tvShowImageCrudRepository.insertIgnore(showId = 10, url = any(), isPrimary = false) }
            verify(exactly = 1) { tvShowImagePrimaryService.setPrimaryForShow(10, "/covers/plex/tv-shows/10/poster.jpg") }
            verify(exactly = 1) { tvShowRepository.save(match { it.coverUrl == "/covers/plex/tv-shows/10/poster.jpg" }) }
        }

    @Test
    fun `should use fallback thumb when image list is empty`() =
        runBlocking {
            val show = TvShow(id = 11, originalTitle = "Dark", description = null, coverUrl = null, fingerprint = "fp2")
            val imageContent = ImageContent("abc".toByteArray(), MediaType.IMAGE_JPEG)

            coEvery { plexApiClient.downloadImageContent(any()) } returns imageContent
            every {
                imageStorageService.saveImageForTvShow(
                    image = any(),
                    provider = "PLEX",
                    showId = 11,
                    fileNameHint = any(),
                )
            } returns "/covers/plex/tv-shows/11/cover.jpg"
            every { tvShowRepository.save(any()) } answers { firstArg() }

            service.ensureShowImagesFromPlex(
                show = show,
                images = emptyList(),
                fallbackThumbPath = "/library/metadata/999/thumb/111",
            )

            verify(exactly = 1) { tvShowImageCrudRepository.insertIgnore(11, "/covers/plex/tv-shows/11/cover.jpg", false) }
            verify(exactly = 1) { tvShowImagePrimaryService.setPrimaryForShow(11, "/covers/plex/tv-shows/11/cover.jpg") }
            io.mockk.slot<TvShow>().also { slot ->
                verify(exactly = 1) { tvShowRepository.save(capture(slot)) }
                assertEquals("/covers/plex/tv-shows/11/cover.jpg", slot.captured.coverUrl)
            }
        }
}
