package dev.marcal.mediapulse.server.service.workout

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

class WorkoutPhotoServiceTest {
    @TempDir
    lateinit var root: Path

    @Test
    fun `validates actual format preserves original bytes and uses generated filename`() {
        val service = WorkoutPhotoService(root.toString())
        for (format in listOf("png", "jpg")) {
            val output = ByteArrayOutputStream()
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), format, output)
            val bytes = output.toByteArray()
            val url = service.save(MockMultipartFile("photo", "../../external.exe", "application/octet-stream", bytes))
            assertTrue(url.startsWith("/covers/workouts/"))
            assertTrue(url.endsWith(".$format"))
            val path = root.resolve("workouts").resolve(url.substringAfterLast('/'))
            assertContentEquals(bytes, Files.readAllBytes(path))
            service.delete(url)
            assertFalse(Files.exists(path))
        }
    }

    @Test
    fun `rejects empty oversized and fake image files without storing them`() {
        val service = WorkoutPhotoService(root.toString())
        for (bytes in listOf(byteArrayOf(), "<svg onload='alert(1)'/>".toByteArray(), ByteArray(10 * 1024 * 1024 + 1))) {
            val error =
                assertFailsWith<ResponseStatusException> {
                    service.save(MockMultipartFile("photo", "photo.png", "image/png", bytes))
                }
            assertEquals(400, error.statusCode.value())
        }
        assertFalse(Files.exists(root.resolve("workouts")))
    }
}
