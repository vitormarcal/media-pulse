package dev.marcal.mediapulse.server.service.image

import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.util.SlugTextUtil
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

    companion object {
        private val logger = org.slf4j.LoggerFactory.getLogger(ImageStorageService::class.java)
    }

    init {
        logger.info("Initializing ImageStorageService with baseDir={}", baseDir.toAbsolutePath())
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
        logger.debug("Saving image. providerDir={}, artistDir={}", providerDir, artistDir)

        Files.createDirectories(artistDir)

        val safeHint = fileNameHint?.let { SlugTextUtil.normalize(it) }

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
                logger.debug("File already exists, skipping write. target={}", target)
            } catch (e: Exception) {
                logger.error(
                    "Failed to write image to disk. target=$target, baseDir=$baseDir, providerDir=$providerDir, artistDir=$artistDir",
                    e,
                )
                throw e
            }
        } else {
            logger.debug("File already exists, nothing to do. target={}", target)
        }

        return "/covers/${provider.lowercase()}/$artistId/$fileName"
    }

    fun saveImageForBook(
        image: ImageContent,
        provider: String, // "HARDCOVER"
        bookId: Long,
        fileNameHint: String? = null,
        editionId: Long? = null,
    ): String {
        val extension = extensionFromContentType(image.contentType)

        val providerDir = baseDir.resolve(provider.lowercase())
        val booksDir = providerDir.resolve("books")
        val bookDir = booksDir.resolve(bookId.toString())
        logger.debug("Saving book image. providerDir={}, bookDir={}", providerDir, bookDir)

        Files.createDirectories(bookDir)

        val safeHint = fileNameHint?.let { SlugTextUtil.normalize(it) }

        val baseName =
            when {
                editionId != null && !safeHint.isNullOrBlank() -> "${editionId}_$safeHint"
                editionId != null -> editionId.toString()
                !safeHint.isNullOrBlank() -> "${bookId}_$safeHint"
                else -> bookId.toString()
            }

        val fileName = "$baseName$extension"
        val target = bookDir.resolve(fileName)

        if (!Files.exists(target)) {
            try {
                Files.write(
                    target,
                    image.bytes,
                    StandardOpenOption.CREATE_NEW,
                )
            } catch (_: FileAlreadyExistsException) {
                logger.debug("File already exists, skipping write. target={}", target)
            } catch (e: Exception) {
                logger.error(
                    "Failed to write book image to disk. target=$target, baseDir=$baseDir, providerDir=$providerDir, bookDir=$bookDir",
                    e,
                )
                throw e
            }
        } else {
            logger.debug("File already exists, nothing to do. target={}", target)
        }

        return "/covers/${provider.lowercase()}/books/$bookId/$fileName"
    }

    fun saveImageForMovie(
        image: ImageContent,
        provider: String, // "PLEX"
        movieId: Long,
        fileNameHint: String? = null,
    ): String {
        val extension = extensionFromContentType(image.contentType)

        val providerDir = baseDir.resolve(provider.lowercase())
        val moviesDir = providerDir.resolve("movies")
        val movieDir = moviesDir.resolve(movieId.toString())
        logger.debug("Saving movie image. providerDir={}, movieDir={}", providerDir, movieDir)

        Files.createDirectories(movieDir)

        val safeHint = fileNameHint?.let { SlugTextUtil.normalize(it) }
        val baseName = if (!safeHint.isNullOrBlank()) "${movieId}_$safeHint" else movieId.toString()
        val fileName = "$baseName$extension"
        val target = movieDir.resolve(fileName)

        if (!Files.exists(target)) {
            try {
                Files.write(
                    target,
                    image.bytes,
                    StandardOpenOption.CREATE_NEW,
                )
            } catch (_: FileAlreadyExistsException) {
                logger.debug("File already exists, skipping write. target={}", target)
            } catch (e: Exception) {
                logger.error(
                    "Failed to write movie image to disk. target=$target, baseDir=$baseDir, providerDir=$providerDir, movieDir=$movieDir",
                    e,
                )
                throw e
            }
        } else {
            logger.debug("File already exists, nothing to do. target={}", target)
        }

        return "/covers/${provider.lowercase()}/movies/$movieId/$fileName"
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
