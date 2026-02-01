package dev.marcal.mediapulse.server.service.hardcover

import dev.marcal.mediapulse.server.integration.hardcover.HardcoverImageClient
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HardcoverArtworkService(
    private val imageClient: HardcoverImageClient,
    private val imageStorageService: ImageStorageService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun ensureBookCoverFromHardcoverUrl(
        bookId: Long,
        bookTitle: String,
        hardcoverImageUrl: String?,
    ): String? {
        if (hardcoverImageUrl.isNullOrBlank()) return null
        if (hardcoverImageUrl.startsWith("/covers/")) return hardcoverImageUrl

        return runCatching {
            val image = imageClient.downloadImage(hardcoverImageUrl)
            imageStorageService.saveImageForBook(
                image = image,
                provider = "HARDCOVER",
                bookId = bookId,
                fileNameHint = bookTitle,
            )
        }.onFailure { ex ->
            logger.warn(
                "Failed to download/store Hardcover book cover. bookId='{}' title='{}' url='{}'",
                bookId,
                bookTitle,
                hardcoverImageUrl,
                ex,
            )
        }.getOrNull()
    }

    suspend fun ensureEditionCoverFromHardcoverUrl(
        bookId: Long,
        editionId: Long,
        editionTitle: String?,
        hardcoverImageUrl: String?,
    ): String? {
        if (hardcoverImageUrl.isNullOrBlank()) return null
        if (hardcoverImageUrl.startsWith("/covers/")) return hardcoverImageUrl

        return runCatching {
            val image = imageClient.downloadImage(hardcoverImageUrl)
            imageStorageService.saveImageForBook(
                image = image,
                provider = "HARDCOVER",
                bookId = bookId,
                editionId = editionId,
                fileNameHint = editionTitle,
            )
        }.onFailure { ex ->
            logger.warn(
                "Failed to download/store Hardcover edition cover. bookId='{}' editionId='{}' url='{}'",
                bookId,
                editionId,
                hardcoverImageUrl,
                ex,
            )
        }.getOrNull()
    }
}
