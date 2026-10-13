package dev.marcal.mediapulse.server.service.hardcover

import dev.marcal.mediapulse.server.integration.hardcover.HardcoverImageClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.repository.hardcover.HardcoverNativeRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class HardcoverArtworkServiceTest {
    private val client = mockk<HardcoverImageClient>()
    private val storage = mockk<LibraryImageStorageService>()
    private val repository = mockk<HardcoverNativeRepository>()
    private val service = HardcoverArtworkService(client, storage, repository)

    @Test
    fun `existing local remote and external covers are preserved without provider downloads`() =
        runBlocking {
            for (url in listOf(
                "/covers/old.png",
                "/api/image-assets/00000000-0000-0000-0000-000000000001/content",
                "https://example.com/old.png",
            )) {
                every { repository.storedBookCover(7) } returns url
                every { repository.storedEditionCover(8) } returns url
                assertNull(service.ensureBookCoverFromHardcoverUrl(7, "Livro", "https://example.com/new.png"))
                assertNull(service.ensureEditionCoverFromHardcoverUrl(7, 8, "Edição", "https://example.com/new.png"))
            }
            coVerify(exactly = 0) { client.downloadImage(any()) }
            verify(exactly = 0) { storage.saveBook(any(), any(), any(), any(), any(), any()) }
        }

    @Test
    fun `missing covers use shared storage and failed upload is retried on next import`() =
        runBlocking {
            val image = ImageContent(byteArrayOf(1), null)
            every { repository.storedBookCover(7) } returns null
            every { repository.storedEditionCover(8) } returns null
            coEvery { client.downloadImage("https://example.com/new.png") } returns image
            every { storage.saveBook(image, 7, "Livro", "HARDCOVER") } throws IllegalStateException("offline")
            assertNull(service.ensureBookCoverFromHardcoverUrl(7, "Livro", "https://example.com/new.png"))
            every { storage.saveBook(image, 7, "Livro", "HARDCOVER") } returns "/api/image-assets/book/content"
            every { storage.saveBook(image, 7, "Edição", "HARDCOVER", 8) } returns "/api/image-assets/edition/content"
            assertEquals(
                "/api/image-assets/book/content",
                service.ensureBookCoverFromHardcoverUrl(7, "Livro", "https://example.com/new.png"),
            )
            assertEquals(
                "/api/image-assets/edition/content",
                service.ensureEditionCoverFromHardcoverUrl(7, 8, "Edição", "https://example.com/new.png"),
            )
        }
}
