package dev.marcal.mediapulse.server.service.workout

import org.springframework.beans.factory.annotation.Value
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.UUID
import javax.imageio.ImageIO

@Service
class WorkoutPhotoService(
    @Value("\${media-pulse.storage.covers-path}") coversPath: String,
) {
    private val directory = Path.of(coversPath).resolve("workouts")

    fun save(photo: MultipartFile): String {
        if (photo.isEmpty || photo.size > 10 * 1024 * 1024) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A foto deve ter até 10 MB e não pode estar vazia.")
        }
        val bytes = photo.bytes
        val extension =
            try {
                ImageIO.createImageInputStream(bytes.inputStream()).use { input ->
                    val readers = ImageIO.getImageReaders(input)
                    require(readers.hasNext())
                    val reader = readers.next()
                    try {
                        reader.input = input
                        val format = reader.formatName.lowercase()
                        require(format in setOf("jpeg", "png"))
                        require(reader.getWidth(0).toLong() * reader.getHeight(0) <= 40_000_000)
                        requireNotNull(reader.read(0))
                        if (format == "jpeg") "jpg" else "png"
                    } finally {
                        reader.dispose()
                    }
                }
            } catch (_: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma foto JPEG ou PNG válida, com até 40 megapixels.")
            }
        Files.createDirectories(directory)
        val name = "${UUID.randomUUID()}.$extension"
        val target = directory.resolve(name)
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW)
        } catch (error: Exception) {
            Files.deleteIfExists(target)
            throw error
        }
        return "/covers/workouts/$name"
    }

    fun delete(photoUrl: String) {
        Files.deleteIfExists(directory.resolve(photoUrl.substringAfterLast('/')))
    }
}
