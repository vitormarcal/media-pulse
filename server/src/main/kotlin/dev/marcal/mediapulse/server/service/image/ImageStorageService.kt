package dev.marcal.mediapulse.server.service.image

import dev.marcal.mediapulse.server.model.image.ImageContent
import org.springframework.beans.factory.annotation.Value
import org.springframework.http.MediaType
import org.springframework.stereotype.Service
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption

@Service
class ImageStorageService(
    @Value("\${media-pulse.storage.covers-path}")
    coverPath: String,
) {
    private val baseDir: Path = Path.of(coverPath)

    init {
        Files.createDirectories(baseDir)
    }

    fun saveImageForAlbum(
        image: ImageContent,
        provider: String, // "PLEX"
        artistId: Long,
        albumId: Long,
        fileNameHint: String? = null,
    ): String {
        val extension = extensionFromContentType(image.contentType)

        val providerDir = baseDir.resolve(provider.lowercase())
        val artistDir = providerDir.resolve(artistId.toString())
        Files.createDirectories(artistDir)

        val safeHint =
            fileNameHint
                ?.lowercase()
                ?.replace("[^a-z0-9]+".toRegex(), "_")
                ?.trim('_')
                ?.take(40)

        val baseName =
            if (!safeHint.isNullOrBlank()) {
                "${albumId}_$safeHint"
            } else {
                albumId.toString()
            }

        val fileName = "$baseName$extension"
        val target = artistDir.resolve(fileName)

        if (!Files.exists(target)) {
            try {
                Files.write(
                    target,
                    image.bytes,
                    StandardOpenOption.CREATE_NEW,
                )
            } catch (_: FileAlreadyExistsException) {
            }
        }

        return "/covers/${provider.lowercase()}/$artistId/$fileName"
    }

    private fun extensionFromContentType(contentType: MediaType?): String =
        when {
            contentType == null -> ".jpg"
            contentType.includes(MediaType.IMAGE_JPEG) -> ".jpg"
            contentType.includes(MediaType.IMAGE_PNG) -> ".png"
            contentType.toString().equals("image/webp", ignoreCase = true) -> ".webp"
            else -> ".jpg"
        }
}
