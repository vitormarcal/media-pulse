package dev.marcal.mediapulse.server.service.image

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse

class LibraryImageStorageServiceTest {
    @TempDir lateinit var root: Path
    private val remote = mockk<VisualLibraryClient>(relaxed = true)

    private fun service() = LibraryImageStorageService(ImageStorageService(root.toString()), remote, WebClient.builder())

    private fun png() =
        ByteArrayOutputStream().use {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", it)
            it.toByteArray()
        }

    @Test
    fun `remote book edition and game store original bytes with separate references and no local copy`() {
        val service = service()
        every { remote.enabled } returns true
        every { remote.upload(any(), any()) } returns "/api/image-assets/00000000-0000-0000-0000-000000000001/content"
        val bytes = png()
        val image = ImageContent(bytes, MediaType.IMAGE_JPEG)
        service.saveBook(image, 7, "Livro", "HARDCOVER")
        service.saveBook(image, 7, "Edição", "HARDCOVER", 8)
        service.saveGame(image, 7, "Jogo", "STEAMGRIDDB", "grid")
        for ((type, id, tag) in listOf(Triple("book", "7", "livro"), Triple("book_edition", "8", "livro"), Triple("game", "7", "jogo"))) {
            verify {
                remote.upload(
                    match { it.bytes.contentEquals(bytes) && it.contentType == MediaType.IMAGE_PNG },
                    match { it.sourceReference.entityType == type && it.sourceReference.entityId == id && tag in it.tags },
                )
            }
        }
        assertEquals(0L, Files.list(root).use { it.count() })
    }

    @Test
    fun `disabled storage preserves original PNG and WebP bytes for books and games`() {
        val service = service()
        val fixtures = listOf(png(), javaClass.getResourceAsStream("/fixtures/images/static.webp")!!.use { it.readBytes() })
        for (bytes in fixtures) {
            val image = ImageContent(bytes, null)
            val book =
                service.saveBook(
                    image,
                    7,
                    "Livro",
                    "MANUAL",
                    fileHint =
                        java.util.UUID
                            .randomUUID()
                            .toString(),
                )
            val game =
                service.saveGame(
                    image,
                    8,
                    "Jogo",
                    "MANUAL",
                    java.util.UUID
                        .randomUUID()
                        .toString(),
                )
            for (url in listOf(book, game)) assertContentEquals(bytes, Files.readAllBytes(root.resolve(url.removePrefix("/covers/"))))
        }
        verify(exactly = 0) { remote.upload(any(), any()) }
    }

    @Test
    fun `local game uploads cannot reuse files pending migration cleanup`() {
        val service = service()
        val image = ImageContent(png(), null)
        every { remote.enabled } returns false
        every { remote.migrationEnabled } returns false
        val original = service.saveGame(image, 7, "Jogo", "STEAMGRIDDB", "grid")
        assertEquals(original, service.saveGame(image, 7, "Jogo", "STEAMGRIDDB", "grid"))
        every { remote.migrationEnabled } returns true
        val first = service.saveGame(image, 7, "Jogo", "STEAMGRIDDB", "grid")
        val second = service.saveGame(image, 7, "Jogo", "STEAMGRIDDB", "grid")
        assertEquals(3, setOf(original, first, second).size)
        Files.delete(root.resolve(original.removePrefix("/covers/")))
        for (url in listOf(first, second)) assertContentEquals(image.bytes, Files.readAllBytes(root.resolve(url.removePrefix("/covers/"))))
    }

    @Test
    fun `empty fake truncated and oversized data never reaches either storage`() {
        val service = service()
        every { remote.enabled } returns true
        for (bytes in listOf(byteArrayOf(), "fake".toByteArray(), png().copyOf(20), ByteArray(10 * 1024 * 1024 + 1))) {
            assertEquals(
                400,
                assertFailsWith<ResponseStatusException> {
                    service.saveBook(ImageContent(bytes, null), 7, "Livro", "HARDCOVER")
                }.statusCode.value(),
            )
        }
        verify(exactly = 0) { remote.upload(any(), any()) }
        assertFalse(Files.exists(root.resolve("hardcover")))
    }
}
