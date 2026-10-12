package dev.marcal.mediapulse.server.service.artwork

import dev.marcal.mediapulse.server.repository.crud.ArtworkImageRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockMultipartFile
import org.springframework.web.server.ResponseStatusException
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ArtworkImageServiceTest {
    private val storage = mockk<ImageStorageService>()
    private val service = ArtworkImageService(mockk(), mockk<ArtworkImageRepository>(), storage)

    @Test
    fun `valid PNG is saved without changing dimensions and fake MIME images are rejected`() {
        val bytes =
            ByteArrayOutputStream().use { output ->
                ImageIO.write(BufferedImage(3, 7, BufferedImage.TYPE_INT_RGB), "png", output)
                output.toByteArray()
            }
        every { storage.saveImageForArtwork(any(), any()) } returns "/covers/artworks/image.png"
        assertEquals("/covers/artworks/image.png", service.upload(MockMultipartFile("image", "test.png", "image/png", bytes)))
        verify { storage.saveImageForArtwork(match { it.bytes.contentEquals(bytes) }, any()) }
        assertFailsWith<ResponseStatusException> {
            service.upload(MockMultipartFile("image", "fake.png", "image/png", "not an image".toByteArray()))
        }
        assertFailsWith<ResponseStatusException> {
            service.upload(
                MockMultipartFile("image", ByteArray(ArtworkImageService.MAX_BYTES + 1)),
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
            assertFailsWith<ResponseStatusException> { service.download(url) }
        }
        assertFailsWith<ResponseStatusException> { service.upload(MockMultipartFile("image", byteArrayOf())) }
        verify(exactly = 0) { storage.saveImageForArtwork(any(), any()) }
    }
}
