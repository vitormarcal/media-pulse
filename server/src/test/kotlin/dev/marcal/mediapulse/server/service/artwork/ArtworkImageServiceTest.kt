package dev.marcal.mediapulse.server.service.artwork

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.artwork.Artwork
import dev.marcal.mediapulse.server.model.artwork.ArtworkArtist
import dev.marcal.mediapulse.server.model.visuallibrary.VisualLibraryUploadMetadata
import dev.marcal.mediapulse.server.repository.crud.ArtworkImageRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
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
import kotlin.test.assertTrue

class ArtworkImageServiceTest {
    private val metadata = VisualLibraryUploadMetadata.forEntity("artwork", 7, "Teste")
    private val storage = mockk<ImageStorageService>()
    private val service = ArtworkImageService(mockk(), mockk<ArtworkImageRepository>(), storage, mockk<VisualLibraryClient>(relaxed = true))

    @TempDir
    lateinit var root: Path

    @Test
    fun `WebP dimensions over forty megapixels are rejected before decoding pixels`() {
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
                service.upload(MockMultipartFile("image", "large.webp", "image/webp", bytes), metadata)
            }.statusCode.value(),
        )
        verify(exactly = 0) { storage.saveImageForArtwork(any(), any()) }
    }

    @Test
    fun `WebP uses detected MIME and preserves static and animated originals in both storage modes`() {
        val client = mockk<VisualLibraryClient>(relaxed = true)
        val local = ArtworkImageService(mockk(), mockk(), ImageStorageService(root.toString()), client)
        for (fixture in listOf("static.webp", "animated.webp")) {
            val bytes = javaClass.getResourceAsStream("/fixtures/images/$fixture")!!.use { it.readBytes() }
            val file = MockMultipartFile("image", "renamed.jpg", "image/jpeg", bytes)
            val url = local.upload(file, metadata)
            assertTrue(url.endsWith(".webp"))
            assertContentEquals(bytes, Files.readAllBytes(root.resolve(url.removePrefix("/covers/"))))
            every { client.enabled } returns true
            every { client.upload(any(), metadata) } returns "/api/image-assets/test/content"
            assertEquals("/api/image-assets/test/content", local.upload(file, metadata))
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
    fun `corrupt WebP is rejected before storage`() {
        val bytes = javaClass.getResourceAsStream("/fixtures/images/static.webp")!!.use { it.readBytes() }
        for (invalid in listOf(bytes.copyOf(bytes.size / 2), "RIFFfakeWEBP".toByteArray())) {
            assertEquals(
                400,
                assertFailsWith<ResponseStatusException> {
                    service.upload(MockMultipartFile("image", "fake.webp", "image/webp", invalid), metadata)
                }.statusCode.value(),
            )
        }
        verify(exactly = 0) { storage.saveImageForArtwork(any(), any()) }
    }

    @Test
    fun `work images and artist portraits carry their own entity ids and cultural metadata`() {
        val works = mockk<ArtworkService>()
        every { works.work(7) } returns
            Artwork(
                id = 7,
                title = "Abaporu",
                artistId = 2,
                type = "Pintura",
                movements = listOf("Modernismo"),
                technique = "Óleo sobre tela",
            )
        every { works.artist(2) } returns ArtworkArtist(id = 2, name = "Tarsila do Amaral", countryOfOrigin = "Brasil")
        val service = ArtworkImageService(works, mockk(), storage, mockk())
        val work = service.artworkMetadata(7)
        assertEquals("artwork", work.sourceReference.entityType)
        assertEquals("7", work.sourceReference.entityId)
        assertEquals(listOf("arte visual", "Abaporu", "Tarsila do Amaral", "Pintura", "Óleo sobre tela"), work.tags)
        assertEquals(listOf("Modernismo"), work.movements)
        val portrait = service.artistMetadata(2)
        assertEquals("artwork_artist", portrait.sourceReference.entityType)
        assertEquals("2", portrait.sourceReference.entityId)
        assertEquals(listOf("artista", "Tarsila do Amaral", "Brasil"), portrait.tags)
    }

    @Test
    fun `enabled remote storage preserves image bytes and bypasses local storage`() {
        val client = mockk<VisualLibraryClient>()
        every { client.enabled } returns true
        val url = "/api/image-assets/00000000-0000-0000-0000-000000000001/content"
        every { client.upload(any(), metadata) } returns url
        val remote = ArtworkImageService(mockk(), mockk(), storage, client)
        val bytes =
            ByteArrayOutputStream().use { output ->
                ImageIO.write(BufferedImage(3, 7, BufferedImage.TYPE_INT_RGB), "jpg", output)
                output.toByteArray()
            }
        assertEquals(url, remote.upload(MockMultipartFile("image", bytes), metadata))
        verify { client.upload(match { it.bytes.contentEquals(bytes) }, metadata) }
        verify(exactly = 0) { storage.saveImageForArtwork(any(), any()) }
        assertFailsWith<ResponseStatusException> { remote.upload(MockMultipartFile("image", "fake".toByteArray()), metadata) }
        verify(exactly = 1) { client.upload(any(), metadata) }
    }

    @Test
    fun `valid PNG is saved without changing dimensions and fake MIME images are rejected`() {
        val bytes =
            ByteArrayOutputStream().use { output ->
                ImageIO.write(BufferedImage(3, 7, BufferedImage.TYPE_INT_RGB), "png", output)
                output.toByteArray()
            }
        every { storage.saveImageForArtwork(any(), any()) } returns "/covers/artworks/image.png"
        assertEquals("/covers/artworks/image.png", service.upload(MockMultipartFile("image", "test.png", "image/png", bytes), metadata))
        verify { storage.saveImageForArtwork(match { it.bytes.contentEquals(bytes) }, any()) }
        assertFailsWith<ResponseStatusException> {
            service.upload(MockMultipartFile("image", "fake.png", "image/png", "not an image".toByteArray()), metadata)
        }
        assertFailsWith<ResponseStatusException> {
            service.upload(
                MockMultipartFile("image", ByteArray(ArtworkImageService.MAX_BYTES + 1)),
                metadata,
            )
        }
    }

    @Test
    fun `downloads reject local addresses unsupported schemes and empty files`() {
        for (url in listOf(
            "http://127.0.0.1/private.png",
            "http://[::1]/private.png",
            "file:///tmp/image.png",
            "https://user:password@example.com/image.png",
        )) {
            assertFailsWith<ResponseStatusException> { service.download(url, metadata) }
        }
        assertFailsWith<ResponseStatusException> { service.upload(MockMultipartFile("image", byteArrayOf()), metadata) }
        verify(exactly = 0) { storage.saveImageForArtwork(any(), any()) }
    }
}
