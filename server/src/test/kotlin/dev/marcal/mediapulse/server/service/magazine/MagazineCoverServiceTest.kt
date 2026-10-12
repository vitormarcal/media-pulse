package dev.marcal.mediapulse.server.service.magazine

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.visuallibrary.VisualLibraryUploadMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.mock.web.MockMultipartFile
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
import kotlin.test.assertTrue

class MagazineCoverServiceTest {
    private val metadata = VisualLibraryUploadMetadata.forEntity("magazine_issue", 7, "Teste")

    @TempDir
    lateinit var root: Path

    @Test
    fun `enabled remote storage does not write local files and deletion preserves shared assets`() {
        val client = mockk<VisualLibraryClient>()
        every { client.enabled } returns true
        val url = "/api/image-assets/00000000-0000-0000-0000-000000000001/content"
        every { client.upload(any(), metadata) } returns url
        val service = MagazineCoverService(root.toString(), client)
        val bytes =
            ByteArrayOutputStream().use { output ->
                ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", output)
                output.toByteArray()
            }
        assertEquals(url, service.save(MockMultipartFile("cover", bytes), metadata))
        verify { client.upload(match { it.bytes.contentEquals(bytes) }, metadata) }
        assertFalse(Files.exists(root.resolve("magazines")))
        Files.createDirectories(root.resolve("magazines"))
        val retained = root.resolve("magazines/content")
        Files.writeString(retained, "must remain")
        service.delete(url)
        assertTrue(Files.exists(retained))
        assertFailsWith<ResponseStatusException> { service.save(MockMultipartFile("cover", "fake".toByteArray()), metadata) }
        verify(exactly = 1) { client.upload(any(), metadata) }
    }

    @Test
    fun `validates actual format preserves original bytes and uses generated filename`() {
        val service = MagazineCoverService(root.toString(), mockk<VisualLibraryClient>(relaxed = true))
        for (format in listOf("png", "jpg")) {
            val output = ByteArrayOutputStream()
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output)
            val bytes = output.toByteArray()
            val url = service.save(MockMultipartFile("photo", "../../external.exe", "application/octet-stream", bytes), metadata)
            assertTrue(url.startsWith("/covers/magazines/"))
            assertTrue(url.endsWith(".$format"))
            val path = root.resolve("magazines").resolve(url.substringAfterLast('/'))
            assertContentEquals(bytes, Files.readAllBytes(path))
            service.delete(url)
            assertFalse(Files.exists(path))
        }
    }

    @Test
    fun `rejects empty oversized and fake image files without storing them`() {
        val service = MagazineCoverService(root.toString(), mockk<VisualLibraryClient>(relaxed = true))
        for (bytes in listOf(byteArrayOf(), "<svg onload='alert(1)'/>".toByteArray(), ByteArray(10 * 1024 * 1024 + 1))) {
            val error =
                assertFailsWith<ResponseStatusException> {
                    service.save(MockMultipartFile("photo", "photo.png", "image/png", bytes), metadata)
                }
            assertEquals(400, error.statusCode.value())
        }
        assertFalse(Files.exists(root.resolve("magazines")))
    }
}
