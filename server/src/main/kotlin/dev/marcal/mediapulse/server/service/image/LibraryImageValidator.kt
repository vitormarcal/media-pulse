package dev.marcal.mediapulse.server.service.image

import dev.marcal.mediapulse.server.model.image.ImageContent
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ResponseStatusException
import javax.imageio.ImageIO

internal object LibraryImageValidator {
    fun validate(bytes: ByteArray): ImageContent {
        if (bytes.isEmpty() || bytes.size > LibraryImageStorageService.MAX_BYTES) bad("Escolha uma imagem de até 10 MB.")
        val format =
            try {
                ImageIO.createImageInputStream(bytes.inputStream()).use { input ->
                    val readers = ImageIO.getImageReaders(input)
                    require(readers.hasNext())
                    val reader = readers.next()
                    try {
                        reader.input = input
                        val format = reader.formatName.lowercase()
                        require(format in setOf("jpeg", "png", "webp"))
                        require(reader.getWidth(0).toLong() * reader.getHeight(0) <= 40_000_000)
                        requireNotNull(reader.read(0))
                        format
                    } finally {
                        reader.dispose()
                    }
                }
            } catch (_: Exception) {
                bad("Escolha uma imagem JPEG, PNG ou WebP válida, com até 40 megapixels.")
            }
        return ImageContent(
            bytes,
            when (format) {
                "jpeg" -> MediaType.IMAGE_JPEG
                "png" -> MediaType.IMAGE_PNG
                else -> MediaType.parseMediaType("image/webp")
            },
        )
    }

    private fun bad(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
