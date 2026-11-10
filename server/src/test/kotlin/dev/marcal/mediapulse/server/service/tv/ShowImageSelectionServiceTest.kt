package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionRequest
import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.integration.tmdb.TmdbMovieImageResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbMovieImagesResponse
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import dev.marcal.mediapulse.server.service.image.TmdbImageSelectionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Optional

class ShowImageSelectionServiceTest {
    private val shows = mockk<TvShowRepository>()
    private val images = mockk<TvShowImageCrudRepository>(relaxUnitFun = true)
    private val tmdb = mockk<TmdbApiClient>()
    private val storage = mockk<LibraryImageStorageService>()
    private val library = mockk<VisualLibraryClient>()
    private val service =
        ShowImageSelectionService(
            shows,
            images,
            TmdbImageSelectionService(tmdb, TmdbProperties(), storage, library),
            TvShowImagePrimaryService(images, shows),
        )

    init {
        every { shows.findById(7) } returns
            Optional.of(TvShow(id = 7, originalTitle = "Série", tmdbId = "42", coverUrl = "current", fingerprint = "show"))
        every { tmdb.fetchShowImages("42") } returns
            TmdbMovieImagesResponse(
                listOf(TmdbMovieImageResponse("/poster.jpg", 780, 1170)),
                listOf(TmdbMovieImageResponse("/backdrop.jpg", 1920, 1080)),
            )
    }

    @Test
    fun `partial save inserts only successful references without touching cover or primary`() {
        val (first, second) = service.candidates(7).images
        val unknown = "0".repeat(64)
        val content = ImageContent(byteArrayOf(1), null)
        every { storage.downloadProviderImage(first.url) } returns content
        every { storage.downloadProviderImage(second.url) } throws IllegalStateException()
        every { storage.saveShow(content, 7, "Série", "TMDB", first.id.take(24)) } returns "stored"
        every { images.lockShowRowForPrimaryUpdate(7) } returns 7

        val result = service.save(7, TmdbImageSelectionRequest(listOf(first.id, second.id, unknown, first.id)))

        assertEquals(listOf(first.id), result.savedIds)
        assertEquals(listOf(second.id, unknown), result.failedIds)
        verify(exactly = 1) { storage.downloadProviderImage(first.url) }
        verifyOrder {
            images.lockShowRowForPrimaryUpdate(7)
            images.insertIgnore(7, "stored", false)
        }
        verify(exactly = 0) {
            shows.updateCover(any(), any())
            images.clearPrimaryForShow(any())
            images.markPrimaryForShow(any(), any())
        }
    }

    @Test
    fun `lookup identifies existing show gallery links`() {
        val option = service.candidates(7).images.first()
        val asset = java.util.UUID.fromString("00000000-0000-0000-0000-000000000001")
        val url = "/api/image-assets/$asset/content"
        every { library.enabled } returns true
        every { storage.downloadProviderImage(option.url) } returns ImageContent(byteArrayOf(1), null)
        every { library.lookupHashes(any()) } answers { firstArg<List<String>>().associateWith { asset } }
        every { images.findByShowIdOrderByIsPrimaryDescIdDesc(7) } returns emptyList()
        every { shows.storedCover(7) } returns url

        assertEquals(
            "IN_GALLERY",
            service
                .lookup(7, TmdbImageSelectionRequest(listOf(option.id)))
                .results
                .single()
                .state,
        )
    }
}
