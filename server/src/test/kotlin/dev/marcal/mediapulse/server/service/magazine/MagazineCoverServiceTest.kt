package dev.marcal.mediapulse.server.service.magazine

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.visuallibrary.VisualLibraryUploadMetadata
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.MediaType
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
    fun `WebP dimensions over forty megapixels are rejected before decoding pixels`() {
        val service = MagazineCoverService(root.toString(), mockk<VisualLibraryClient>(relaxed = true))
        val bytes = javaClass.getResourceAsStream("/fixtures/images/static.webp")!!.use { it.readBytes() }
        // VP8 frame-header dimensions are little-endian, starting at byte 26 in this fixture.
        java.nio.ByteBuffer.wrap(bytes).order(java.nio.ByteOrder.LITTLE_ENDIAN).apply {
            putShort(26, 10_000.toShort())
            putShort(28, 10_000.toShort())
        }
        ImageIO.createImageInputStream(bytes.inputStream()).use { input ->
            val reader = ImageIO.getImageReaders(input).next()
            try {
                reader.input = input
                assertEquals(10_000, reader.getWidth(0))
                assertEquals(10_000, reader.getHeight(0))
            } finally {
                reader.dispose()
            }
        }
        assertEquals(
            400,
            assertFailsWith<ResponseStatusException> {
                service.save(MockMultipartFile("cover", "large.webp", "image/webp", bytes), metadata)
            }.statusCode.value(),
        )
        assertFalse(Files.exists(root.resolve("magazines")))
    }

    @Test
    fun `static and animated WebP retain original bytes in local and remote storage`() {
        val client = mockk<VisualLibraryClient>(relaxed = true)
        val local = MagazineCoverService(root.toString(), client)
        for (fixture in listOf("static.webp", "animated.webp")) {
            val bytes = javaClass.getResourceAsStream("/fixtures/images/$fixture")!!.use { it.readBytes() }
            val file = MockMultipartFile("cover", "renamed.png", "image/png", bytes)
            val url = local.save(file, metadata)
            assertTrue(url.endsWith(".webp"))
            val target = root.resolve("magazines").resolve(url.substringAfterLast('/'))
            assertContentEquals(bytes, Files.readAllBytes(target))
            local.delete(url)
            assertFalse(Files.exists(target))
            every { client.enabled } returns true
            every { client.upload(any(), metadata) } returns "/api/image-assets/test/content"
            assertEquals("/api/image-assets/test/content", local.save(file, metadata))
            verify {
                client.upload(
                    match {
                        it.bytes.contentEquals(bytes) && it.contentType == MediaType.parseMediaType("image/webp")
                    },
                    metadata,
                )
            }
            every { client.enabled } returns false
        }
    }

    @Test
    fun `truncated and fake WebP are rejected without local writes or remote uploads`() {
        val client = mockk<VisualLibraryClient>(relaxed = true)
        every { client.enabled } returns true
        val service = MagazineCoverService(root.toString(), client)
        val bytes = javaClass.getResourceAsStream("/fixtures/images/static.webp")!!.use { it.readBytes() }
        for (invalid in listOf(bytes.copyOf(bytes.size / 2), "RIFFfakeWEBP".toByteArray())) {
            assertEquals(
                400,
                assertFailsWith<ResponseStatusException> {
                    service.save(MockMultipartFile("cover", "fake.webp", "image/webp", invalid), metadata)
                }.statusCode.value(),
            )
        }
        verify(exactly = 0) { client.upload(any(), any()) }
        assertFalse(Files.exists(root.resolve("magazines")))
    }

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
