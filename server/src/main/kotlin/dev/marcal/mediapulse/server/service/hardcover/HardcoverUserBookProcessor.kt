package dev.marcal.mediapulse.server.service.hardcover

import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverContribution
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverUserBook
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.book.BookReadSource
import dev.marcal.mediapulse.server.repository.hardcover.HardcoverNativeRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import dev.marcal.mediapulse.server.util.TxUtil
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Service
class HardcoverUserBookProcessor(
    private val nativeRepo: HardcoverNativeRepository,
    private val txUtil: TxUtil,
    private val artworkService: HardcoverArtworkService,
) {
    private data class CoverInfo(
        val bookId: Long,
        val bookTitle: String,
        val bookCoverUrl: String?,
        val editionId: Long?,
        val editionTitle: String?,
        val editionCoverUrl: String?,
    )

    suspend fun process(item: HardcoverUserBook) {
        val coverInfo = txUtil.inTx { processInTx(item) }

        val bookCoverLocalPath =
            artworkService.ensureBookCoverFromHardcoverUrl(
                bookId = coverInfo.bookId,
                bookTitle = coverInfo.bookTitle,
                hardcoverImageUrl = coverInfo.bookCoverUrl,
            )

        val editionCoverLocalPath =
            if (coverInfo.editionId != null) {
                artworkService.ensureEditionCoverFromHardcoverUrl(
                    bookId = coverInfo.bookId,
                    editionId = coverInfo.editionId,
                    editionTitle = coverInfo.editionTitle,
                    hardcoverImageUrl = coverInfo.editionCoverUrl,
                )
            } else {
                null
            }

        if (bookCoverLocalPath != null || editionCoverLocalPath != null) {
            txUtil.inTx {
                if (bookCoverLocalPath != null) {
                    nativeRepo.updateBookCover(coverInfo.bookId, bookCoverLocalPath)
                }
                if (editionCoverLocalPath != null && coverInfo.editionId != null) {
                    nativeRepo.updateEditionCover(coverInfo.editionId, editionCoverLocalPath)
                }
            }
        }
    }

    private fun normalizeRole(raw: String?): String {
        val base = raw?.trim()?.ifBlank { null } ?: return "AUTHOR"
        val role =
            base
                .uppercase()
                .replace(Regex("[^A-Z0-9]+"), "_")
                .replace(Regex("_+"), "_")
                .trim('_')
                .take(64)
        return role.ifBlank { "CONTRIBUTOR" }
    }

    private fun contributionNameRolePairs(contribs: List<HardcoverContribution>?): List<Pair<String, String>> =
        contribs
            .orEmpty()
            .asSequence()
            .mapNotNull { c ->
                val name =
                    c.author
                        ?.name
                        ?.trim()
                        ?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val role = if (c.contribution == null) "AUTHOR" else normalizeRole(c.contribution)
                name to role
            }.distinct()
            .toList()

    private fun processInTx(item: HardcoverUserBook): CoverInfo {
        val edition = item.edition
        val book = item.book

        val contribPairs = contributionNameRolePairs(edition?.contributions)
        val authorIdsByName: Map<String, Long> =
            contribPairs
                .asSequence()
                .map { it.first }
                .distinct()
                .associateWith { name ->
                    val fp = FingerprintUtil.authorFp(name)
                    nativeRepo.ensureAuthorId(name, fp)
                }

        val bookTitle = book?.title?.ifBlank { "Unknown" } ?: "Unknown"
        val bookFingerprint = FingerprintUtil.bookFp(bookTitle)
        val bookReleaseDate = parseLocalDate(book?.releaseDate) ?: parseLocalDate(edition?.releaseDate)
        val bookDescription = edition?.editionInformation
        val bookCoverUrl = edition?.image?.url
        val rating = item.rating
        val reviewRaw = if (item.hasReview == true) item.reviewRaw else null
        val reviewedAt = parseInstant(item.reviewedAt)
        val updatedAt = parseInstant(item.updatedAt)

        val bookId =
            nativeRepo.ensureBookId(
                title = bookTitle,
                releaseDate = bookReleaseDate,
                description = bookDescription,
                coverUrl = null,
                rating = rating,
                reviewRaw = reviewRaw,
                reviewedAt = reviewedAt,
                sourceUpdatedAt = updatedAt,
                fingerprint = bookFingerprint,
            )

        // relaciona contribuições (AUTHOR / TRANSLATOR / ILLUSTRATOR / etc)
        for ((name, role) in contribPairs) {
            val authorId = authorIdsByName[name] ?: continue
            nativeRepo.linkBookAuthor(bookId = bookId, authorId = authorId, role = role)
        }

        val reads = item.userBookReads.orEmpty()

        val editionKeyId = edition?.id ?: reads.firstOrNull()?.editionId
        val editionFingerprint =
            if (!edition?.isbn13.isNullOrBlank() || editionKeyId != null) {
                FingerprintUtil.editionFp(edition?.isbn13, editionKeyId)
            } else {
                null
            }

        val editionId =
            editionFingerprint?.let {
                nativeRepo.ensureEditionId(
                    bookId = bookId,
                    title = edition?.title,
                    isbn10 = edition?.isbn10,
                    isbn13 = edition?.isbn13,
                    pages = edition?.pages,
                    language = edition?.language?.code2,
                    publisher = edition?.publisher?.name,
                    format = edition?.editionFormat,
                    coverUrl = null,
                    fingerprint = it,
                )
            }
        if (editionId != null) {
            FingerprintUtil.normalizeIsbn(edition?.isbn13)?.let { isbn ->
                nativeRepo.insertExternalIdentifier(
                    entityType = EntityType.BOOK_EDITION,
                    entityId = editionId,
                    provider = Provider.ISBN_13,
                    externalId = isbn,
                )
            }
            FingerprintUtil.normalizeIsbn(edition?.isbn10)?.let { isbn ->
                nativeRepo.insertExternalIdentifier(
                    entityType = EntityType.BOOK_EDITION,
                    entityId = editionId,
                    provider = Provider.ISBN_10,
                    externalId = isbn,
                )
            }
        }

        val status = HardcoverStatusMapper.mapSlug(item.userBookStatus?.slug)

        if (reads.isEmpty()) {
            val fallbackStartedAt = parseInstant(item.firstStartedReadingDate)
            val fallbackFinishedAt =
                parseInstant(item.lastReadDate)
                    ?: parseInstant(item.firstReadDate)

            nativeRepo.upsertBookRead(
                bookId = bookId,
                editionId = editionId,
                source = BookReadSource.HARDCOVER.name,
                sourceEventId = item.id,
                status = status.name,
                startedAt = fallbackStartedAt,
                finishedAt = fallbackFinishedAt,
                progressPct = null,
                progressPages = null,
                updatedAt = updatedAt,
            )
            return CoverInfo(
                bookId = bookId,
                bookTitle = bookTitle,
                bookCoverUrl = bookCoverUrl,
                editionId = editionId,
                editionTitle = edition?.title,
                editionCoverUrl = edition?.image?.url,
            )
        }

        for (r in reads) {
            val readId = r.id ?: continue

            val startedAt = parseInstant(r.startedAt) ?: parseInstant(item.firstStartedReadingDate)
            val finishedAt =
                parseInstant(r.finishedAt)
                    ?: parseInstant(item.lastReadDate)
                    ?: parseInstant(item.firstReadDate)
            val progressPct = r.progress?.let { BigDecimal.valueOf(it) }
            val progressPages = r.progressPages

            nativeRepo.upsertBookRead(
                bookId = bookId,
                editionId = editionId,
                source = BookReadSource.HARDCOVER.name,
                sourceEventId = readId,
                status = status.name,
                startedAt = startedAt,
                finishedAt = finishedAt,
                progressPct = progressPct,
                progressPages = progressPages,
                updatedAt = updatedAt,
            )
        }

        return CoverInfo(
            bookId = bookId,
            bookTitle = bookTitle,
            bookCoverUrl = bookCoverUrl,
            editionId = editionId,
            editionTitle = edition?.title,
            editionCoverUrl = edition?.image?.url,
        )
    }

    private fun parseInstant(value: String?): Instant? =
        value?.let {
            runCatching { Instant.parse(it) }.getOrNull()
                ?: runCatching {
                    java.time.LocalDateTime
                        .parse(it)
                        .atZone(java.time.ZoneOffset.UTC)
                        .toInstant()
                }.getOrNull()
                ?: runCatching { LocalDate.parse(it).atStartOfDay(java.time.ZoneOffset.UTC).toInstant() }.getOrNull()
        }

    private fun parseLocalDate(value: String?): LocalDate? {
        if (value.isNullOrBlank()) return null
        return runCatching { LocalDate.parse(value) }.getOrNull()
            ?: runCatching {
                if (value.length == 4 && value.all { it.isDigit() }) {
                    LocalDate.of(value.toInt(), 1, 1)
                } else {
                    null
                }
            }.getOrNull()
    }
}
