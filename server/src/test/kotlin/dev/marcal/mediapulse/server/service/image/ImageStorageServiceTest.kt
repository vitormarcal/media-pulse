package dev.marcal.mediapulse.server.service.image

import dev.marcal.mediapulse.server.model.image.ImageContent
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.http.MediaType
import java.nio.file.Path
import kotlin.io.path.exists
import kotlin.io.path.readBytes
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ImageStorageServiceTest {
    @TempDir
    lateinit var tempDir: Path

    private fun service(): ImageStorageService = ImageStorageService(coverPath = tempDir.toString())

    @Test
    fun `should save jpeg image with sanitized filename and return public path`() {
        val service = service()

        val image =
            ImageContent(
                bytes = byteArrayOf(1, 2, 3),
                contentType = MediaType.IMAGE_JPEG,
            )

        val path =
            service.saveImageForAlbum(
                image = image,
                provider = "SPOTIFY",
                artistId = 10L,
                albumId = 20L,
                fileNameHint = "Ride the Lightning!!! (1984)",
            )

        assertEquals(
            "/covers/spotify/10/20_ride_the_lightning_1984.jpg",
            path,
        )

        val file =
            tempDir
                .resolve("spotify")
                .resolve("10")
                .resolve("20_ride_the_lightning_1984.jpg")

        assertTrue(file.exists())
        assertEquals(image.bytes.toList(), file.readBytes().toList())
    }

    @Test
    fun `should fallback to albumId when filename hint is null`() {
        val service = service()

        val image =
            ImageContent(
                bytes = byteArrayOf(9, 9),
                contentType = MediaType.IMAGE_PNG,
            )

        val path =
            service.saveImageForAlbum(
                image = image,
                provider = "PLEX",
                artistId = 1L,
                albumId = 99L,
                fileNameHint = null,
            )

        assertEquals(
            "/covers/plex/1/99.png",
            path,
        )

        val file =
            tempDir
                .resolve("plex")
                .resolve("1")
                .resolve("99.png")

        assertTrue(file.exists())
    }

    @Test
    fun `should use webp extension when content type is webp`() {
        val service = service()

        val image =
            ImageContent(
                bytes = byteArrayOf(7),
                contentType = MediaType.valueOf("image/webp"),
            )

        val path =
            service.saveImageForAlbum(
                image = image,
                provider = "SPOTIFY",
                artistId = 2L,
                albumId = 3L,
                fileNameHint = "Cover",
            )

        assertEquals(
            "/covers/spotify/2/3_cover.webp",
            path,
        )

        val file =
            tempDir
                .resolve("spotify")
                .resolve("2")
                .resolve("3_cover.webp")

        assertTrue(file.exists())
    }

    @Test
    fun `should default to jpg when content type is null or unknown`() {
        val service = service()

        val image =
            ImageContent(
                bytes = byteArrayOf(1),
                contentType = null,
            )

        val path =
            service.saveImageForAlbum(
                image = image,
                provider = "SPOTIFY",
                artistId = 5L,
                albumId = 6L,
                fileNameHint = "Any",
            )

        assertEquals(
            "/covers/spotify/5/6_any.jpg",
            path,
        )

        val file =
            tempDir
                .resolve("spotify")
                .resolve("5")
                .resolve("6_any.jpg")

        assertTrue(file.exists())
    }

    @Test
    fun `should not overwrite existing file`() {
        val service = service()

        val first =
            ImageContent(
                bytes = byteArrayOf(1, 1, 1),
                contentType = MediaType.IMAGE_JPEG,
            )

        val second =
            ImageContent(
                bytes = byteArrayOf(9, 9, 9),
                contentType = MediaType.IMAGE_JPEG,
            )

        val path1 =
            service.saveImageForAlbum(
                image = first,
                provider = "SPOTIFY",
                artistId = 7L,
                albumId = 8L,
                fileNameHint = "Cover",
            )

        val path2 =
            service.saveImageForAlbum(
                image = second,
                provider = "SPOTIFY",
                artistId = 7L,
                albumId = 8L,
                fileNameHint = "Cover",
            )

        assertEquals(path1, path2)

        val file =
            tempDir
                .resolve("spotify")
                .resolve("7")
                .resolve("8_cover.jpg")

        assertEquals(first.bytes.toList(), file.readBytes().toList())
    }
}
