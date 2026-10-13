package dev.marcal.mediapulse.server.service.hardcover

import dev.marcal.mediapulse.server.integration.hardcover.HardcoverImageClient
import dev.marcal.mediapulse.server.repository.hardcover.HardcoverNativeRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HardcoverArtworkService(
    private val imageClient: HardcoverImageClient,
    private val imageStorageService: LibraryImageStorageService,
    private val repository: HardcoverNativeRepository,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun ensureBookCoverFromHardcoverUrl(
        bookId: Long,
        bookTitle: String,
        hardcoverImageUrl: String?,
    ): String? {
        if (!repository.storedBookCover(bookId).isNullOrBlank()) return null
        if (hardcoverImageUrl.isNullOrBlank()) return null
        if (hardcoverImageUrl.startsWith("/covers/")) return hardcoverImageUrl

        return runCatching {
            val image = imageClient.downloadImage(hardcoverImageUrl)
            imageStorageService.saveBook(
                image = image,
                provider = "HARDCOVER",
                bookId = bookId,
                title = bookTitle,
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
        if (!repository.storedEditionCover(editionId).isNullOrBlank()) return null
        if (hardcoverImageUrl.isNullOrBlank()) return null
        if (hardcoverImageUrl.startsWith("/covers/")) return hardcoverImageUrl

        return runCatching {
            val image = imageClient.downloadImage(hardcoverImageUrl)
            imageStorageService.saveBook(
                image = image,
                provider = "HARDCOVER",
                bookId = bookId,
                editionId = editionId,
                title = editionTitle,
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
