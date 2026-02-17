package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.books.AuthorDto
import dev.marcal.mediapulse.server.api.books.BookCardDto
import dev.marcal.mediapulse.server.api.books.BookDetailsResponse
import dev.marcal.mediapulse.server.api.books.BookReadStatus
import dev.marcal.mediapulse.server.api.books.BooksListResponse
import dev.marcal.mediapulse.server.api.books.BooksSearchResponse
import dev.marcal.mediapulse.server.api.books.BooksSummaryResponse
import dev.marcal.mediapulse.server.api.books.EditionDto
import dev.marcal.mediapulse.server.api.books.RangeDto
import dev.marcal.mediapulse.server.api.books.ReadCardDto
import dev.marcal.mediapulse.server.api.books.SummaryCountsDto
import dev.marcal.mediapulse.server.api.books.TopAuthorDto
import dev.marcal.mediapulse.server.api.books.YearReadsResponse
import dev.marcal.mediapulse.server.api.books.YearStatsDto
import jakarta.persistence.EntityManager
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException
import java.sql.Date
import java.sql.Timestamp
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@Repository
class BookQueryRepository(
    private val entityManager: EntityManager,
) {
    fun getYearReads(year: Int): YearReadsResponse {
        val range = resolveYearRange(year)

        val currentlyReadingRows =
            fetchReadRows(
                where =
                    """
                    br.status = :status
                    AND br.started_at <= :e
                    AND (br.finished_at IS NULL OR br.finished_at >= :s)
                    """.trimIndent(),
                orderBy = "br.started_at DESC NULLS LAST, br.created_at DESC",
                params =
                    mapOf(
                        "status" to BookReadStatus.CURRENTLY_READING.name,
                        "s" to range.start,
                        "e" to range.end,
                    ),
            )

        val finishedRows =
            fetchReadRows(
                where = "br.status = :status AND br.finished_at BETWEEN :s AND :e",
                orderBy = "br.finished_at DESC NULLS LAST",
                params =
                    mapOf(
                        "status" to BookReadStatus.READ.name,
                        "s" to range.start,
                        "e" to range.end,
                    ),
            )

        val pausedRows =
            fetchReadRows(
                where = "br.status = :status AND br.updated_at BETWEEN :s AND :e",
                orderBy = "br.updated_at DESC NULLS LAST",
                params =
                    mapOf(
                        "status" to BookReadStatus.PAUSED.name,
                        "s" to range.start,
                        "e" to range.end,
                    ),
            )

        val didNotFinishRows =
            fetchReadRows(
                where = "br.status = :status AND br.updated_at BETWEEN :s AND :e",
                orderBy = "br.updated_at DESC NULLS LAST",
                params =
                    mapOf(
                        "status" to BookReadStatus.DID_NOT_FINISH.name,
                        "s" to range.start,
                        "e" to range.end,
                    ),
            )

        val wantToReadRows =
            fetchReadRows(
                // WANT_TO_READ is anchored on when the entry was created.
                where = "br.status = :status AND br.created_at BETWEEN :s AND :e",
                orderBy = "br.updated_at DESC NULLS LAST, br.created_at DESC",
                params =
                    mapOf(
                        "status" to BookReadStatus.WANT_TO_READ.name,
                        "s" to range.start,
                        "e" to range.end,
                    ),
            )

        val unknownRows =
            fetchReadRows(
                where = "br.status = :status AND br.updated_at BETWEEN :s AND :e",
                orderBy = "br.updated_at DESC NULLS LAST",
                params =
                    mapOf(
                        "status" to BookReadStatus.UNKNOWN.name,
                        "s" to range.start,
                        "e" to range.end,
                    ),
            )

        val allRows =
            (currentlyReadingRows + finishedRows + pausedRows + didNotFinishRows + wantToReadRows + unknownRows)

        val authorsByBookId = fetchAuthorsByBookIds(allRows.map { it.bookId }.toSet())

        val currentlyReading = currentlyReadingRows.map { it.toDto(authorsByBookId) }
        val finished = finishedRows.map { it.toDto(authorsByBookId) }
        val paused = pausedRows.map { it.toDto(authorsByBookId) }
        val didNotFinish = didNotFinishRows.map { it.toDto(authorsByBookId) }
        val wantToRead = wantToReadRows.map { it.toDto(authorsByBookId) }
        val unknown = unknownRows.map { it.toDto(authorsByBookId) }

        // pagesFinished is null when no READ entry has an edition page count.
        val finishedPages =
            finishedRows
                .mapNotNull { it.editionPages }
                .takeIf { it.isNotEmpty() }
                ?.sumOf { it.toLong() }

        val stats =
            YearStatsDto(
                finishedCount = finished.size.toLong(),
                currentlyReadingCount = currentlyReading.size.toLong(),
                wantCount = wantToRead.size.toLong(),
                didNotFinishCount = didNotFinish.size.toLong(),
                pausedCount = paused.size.toLong(),
                pagesFinished = finishedPages,
            )

        return YearReadsResponse(
            year = year,
            range = range,
            currentlyReading = currentlyReading,
            finished = finished,
            paused = paused,
            didNotFinish = didNotFinish,
            wantToRead = wantToRead,
            unknown = unknown,
            stats = stats,
        )
    }

    fun getBookDetails(bookId: Long): BookDetailsResponse {
        val book = fetchBookDetailsRow(bookId)

        val authors = fetchAuthorsByBookId(bookId)
        val editions = fetchEditionsByBookId(bookId)

        val reads =
            fetchReadRows(
                where = "br.book_id = :bookId",
                orderBy = "COALESCE(br.finished_at, br.started_at, br.created_at) DESC",
                params = mapOf("bookId" to bookId),
            ).map { row ->
                row.toDto(mapOf(bookId to authors))
            }

        return BookDetailsResponse(
            bookId = book.bookId,
            slug = book.slug,
            title = book.title,
            description = book.description,
            coverUrl = book.coverUrl,
            releaseDate = book.releaseDate,
            rating = book.rating,
            reviewRaw = book.reviewRaw,
            reviewedAt = book.reviewedAt,
            authors = authors,
            editions = editions,
            reads = reads,
        )
    }

    fun getBookDetailsBySlug(slug: String): BookDetailsResponse {
        val book = fetchBookDetailsRowBySlug(slug)
        return getBookDetails(book.bookId)
    }

    fun listReads(
        status: BookReadStatus?,
        limit: Int,
        cursor: String?,
    ): BooksListResponse {
        val cursorId = parseCursor(cursor)
        val params = mutableMapOf<String, Any>()
        val whereParts = mutableListOf<String>()

        if (status != null) {
            whereParts.add("br.status = :status")
            params["status"] = status.name
        }

        if (cursorId != null) {
            whereParts.add("br.id < :cursorId")
            params["cursorId"] = cursorId
        }

        val where =
            if (whereParts.isEmpty()) {
                "1 = 1"
            } else {
                whereParts.joinToString(" AND ")
            }

        val rows =
            fetchReadRows(
                where = where,
                orderBy = "br.id DESC",
                params = params,
                limit = limit,
            )

        val authorsByBookId = fetchAuthorsByBookIds(rows.map { it.bookId }.toSet())
        val items = rows.map { it.toDto(authorsByBookId) }
        val nextCursor = items.lastOrNull()?.let { buildCursor(it.readId) }

        return BooksListResponse(
            items = items,
            nextCursor = if (items.size == limit) nextCursor else null,
        )
    }

    fun search(
        q: String,
        limit: Int,
    ): BooksSearchResponse {
        val like = "%${q.lowercase()}%"

        val authors =
            entityManager
                .createNativeQuery(
                    """
                    SELECT a.id, a.name
                    FROM authors a
                    WHERE LOWER(a.name) LIKE :q
                    ORDER BY a.name
                    """.trimIndent(),
                ).setParameter("q", like)
                .setMaxResults(limit)
                .resultList
                .map {
                    val row = it as Array<*>
                    AuthorDto(
                        id = (row[0] as Number).toLong(),
                        name = row[1] as String,
                    )
                }

        val bookRows =
            entityManager
                .createNativeQuery(
                    """
                    SELECT b.id, b.slug, b.title, b.cover_url, b.release_date, b.rating, b.reviewed_at
                    FROM books b
                    WHERE LOWER(b.title) LIKE :q
                    ORDER BY b.title
                    """.trimIndent(),
                ).setParameter("q", like)
                .setMaxResults(limit)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    BookRow(
                        bookId = (fields[0] as Number).toLong(),
                        slug = fields[1] as String,
                        title = fields[2] as String,
                        coverUrl = fields[3] as String?,
                        releaseDate = asLocalDate(fields[4]),
                        rating = asDouble(fields[5]),
                        reviewedAt = asInstant(fields[6]),
                    )
                }

        val authorsByBookId = fetchAuthorsByBookIds(bookRows.map { it.bookId }.toSet())

        val books =
            bookRows.map { row ->
                BookCardDto(
                    bookId = row.bookId,
                    slug = row.slug,
                    title = row.title,
                    coverUrl = row.coverUrl,
                    releaseDate = row.releaseDate,
                    rating = row.rating,
                    reviewedAt = row.reviewedAt,
                    authors = authorsByBookId[row.bookId].orEmpty(),
                )
            }

        return BooksSearchResponse(
            books = books,
            authors = authors,
        )
    }

    fun summary(
        range: String,
        start: Instant?,
        end: Instant?,
    ): BooksSummaryResponse {
        val resolvedRange = resolveSummaryRange(range, start, end)

        val finished =
            countReads(
                """
                SELECT COUNT(*)
                FROM book_reads br
                WHERE br.status = 'READ'
                  AND br.finished_at BETWEEN :s AND :e
                """.trimIndent(),
                resolvedRange,
            )

        val reading =
            countReads(
                """
                SELECT COUNT(*)
                FROM book_reads br
                WHERE br.status = 'CURRENTLY_READING'
                  AND br.started_at <= :e
                  AND (br.finished_at IS NULL OR br.finished_at >= :s)
                """.trimIndent(),
                resolvedRange,
            )

        val want =
            countReads(
                // WANT_TO_READ uses created_at to represent when it entered the list.
                """
                SELECT COUNT(*)
                FROM book_reads br
                WHERE br.status = 'WANT_TO_READ'
                  AND br.created_at BETWEEN :s AND :e
                """.trimIndent(),
                resolvedRange,
            )

        val dnf =
            countReads(
                """
                SELECT COUNT(*)
                FROM book_reads br
                WHERE br.status = 'DID_NOT_FINISH'
                  AND br.updated_at BETWEEN :s AND :e
                """.trimIndent(),
                resolvedRange,
            )

        val paused =
            countReads(
                """
                SELECT COUNT(*)
                FROM book_reads br
                WHERE br.status = 'PAUSED'
                  AND br.updated_at BETWEEN :s AND :e
                """.trimIndent(),
                resolvedRange,
            )

        val counts =
            SummaryCountsDto(
                finished = finished,
                reading = reading,
                want = want,
                dnf = dnf,
                paused = paused,
                total = finished + reading + want + dnf + paused,
            )

        val topAuthors =
            entityManager
                .createNativeQuery(
                    """
                    SELECT a.id, a.name, COUNT(br.id) AS finished_count
                    FROM book_reads br
                    JOIN book_authors ba ON ba.book_id = br.book_id
                    JOIN authors a ON a.id = ba.author_id
                    WHERE br.status = 'READ'
                      AND br.finished_at BETWEEN :s AND :e
                    GROUP BY a.id, a.name
                    ORDER BY COUNT(br.id) DESC
                    """.trimIndent(),
                ).setParameter("s", resolvedRange.start)
                .setParameter("e", resolvedRange.end)
                .setMaxResults(10)
                .resultList
                .map { row ->
                    val fields = row as Array<*>
                    TopAuthorDto(
                        authorId = (fields[0] as Number).toLong(),
                        authorName = fields[1] as String,
                        finishedCount = (fields[2] as Number).toLong(),
                    )
                }

        return BooksSummaryResponse(
            range = RangeDto(resolvedRange.start, resolvedRange.end),
            counts = counts,
            topAuthors = topAuthors,
        )
    }

    private fun fetchReadRows(
        where: String,
        orderBy: String,
        params: Map<String, Any>,
        limit: Int? = null,
    ): List<ReadRow> {
        val query =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      br.id AS read_id,
                      br.status AS status,
                      br.started_at AS started_at,
                      br.finished_at AS finished_at,
                      br.progress_pct AS progress_pct,
                      br.progress_pages AS progress_pages,
                      br.source AS source,
                      br.created_at AS created_at,
                      br.updated_at AS updated_at,
                      b.id AS book_id,
                      b.title AS book_title,
                      b.slug AS book_slug,
                      b.cover_url AS book_cover_url,
                      b.release_date AS book_release_date,
                      b.rating AS book_rating,
                      b.reviewed_at AS book_reviewed_at,
                      be.id AS edition_id,
                      be.title AS edition_title,
                      be.isbn_10 AS edition_isbn_10,
                      be.isbn_13 AS edition_isbn_13,
                      be.pages AS edition_pages,
                      be.language AS edition_language,
                      be.publisher AS edition_publisher,
                      be.format AS edition_format,
                      be.cover_url AS edition_cover_url
                    FROM book_reads br
                    JOIN books b ON b.id = br.book_id
                    LEFT JOIN book_editions be ON be.id = br.edition_id
                    WHERE $where
                    ORDER BY $orderBy
                    """.trimIndent(),
                )
        params.forEach { (key, value) -> query.setParameter(key, value) }
        if (limit != null) {
            query.setMaxResults(limit)
        }

        return query.resultList.map { row ->
            val fields = row as Array<*>
            ReadRow(
                readId = (fields[0] as Number).toLong(),
                status = parseStatus(fields[1]),
                startedAt = asInstant(fields[2]),
                finishedAt = asInstant(fields[3]),
                progressPct = asDouble(fields[4]),
                progressPages = (fields[5] as Number?)?.toInt(),
                source = fields[6] as String,
                createdAt = asInstant(fields[7]),
                updatedAt = asInstant(fields[8]),
                bookId = (fields[9] as Number).toLong(),
                bookTitle = fields[10] as String,
                bookSlug = fields[11] as String,
                bookCoverUrl = fields[12] as String?,
                bookReleaseDate = asLocalDate(fields[13]),
                bookRating = asDouble(fields[14]),
                bookReviewedAt = asInstant(fields[15]),
                editionId = (fields[16] as Number?)?.toLong(),
                editionTitle = fields[17] as String?,
                editionIsbn10 = fields[18] as String?,
                editionIsbn13 = fields[19] as String?,
                editionPages = (fields[20] as Number?)?.toInt(),
                editionLanguage = fields[21] as String?,
                editionPublisher = fields[22] as String?,
                editionFormat = fields[23] as String?,
                editionCoverUrl = fields[24] as String?,
            )
        }
    }

    private fun fetchAuthorsByBookId(bookId: Long): List<AuthorDto> =
        entityManager
            .createNativeQuery(
                """
                SELECT a.id, a.name
                FROM book_authors ba
                JOIN authors a ON a.id = ba.author_id
                WHERE ba.book_id = :bookId
                ORDER BY a.name
                """.trimIndent(),
            ).setParameter("bookId", bookId)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                AuthorDto(
                    id = (fields[0] as Number).toLong(),
                    name = fields[1] as String,
                )
            }

    private fun fetchAuthorsByBookIds(bookIds: Set<Long>): Map<Long, List<AuthorDto>> {
        if (bookIds.isEmpty()) return emptyMap()

        val rows =
            entityManager
                .createNativeQuery(
                    """
                    SELECT ba.book_id, a.id, a.name
                    FROM book_authors ba
                    JOIN authors a ON a.id = ba.author_id
                    WHERE ba.book_id IN (:bookIds)
                    ORDER BY ba.book_id, a.name
                    """.trimIndent(),
                ).setParameter("bookIds", bookIds)
                .resultList

        val byBookId = mutableMapOf<Long, MutableList<AuthorDto>>()
        rows.forEach { row ->
            val fields = row as Array<*>
            val bookId = (fields[0] as Number).toLong()
            val author =
                AuthorDto(
                    id = (fields[1] as Number).toLong(),
                    name = fields[2] as String,
                )
            byBookId.getOrPut(bookId) { mutableListOf() }.add(author)
        }

        return byBookId
    }

    private fun fetchEditionsByBookId(bookId: Long): List<EditionDto> =
        entityManager
            .createNativeQuery(
                """
                SELECT id, title, isbn_10, isbn_13, pages, language, publisher, format, cover_url
                FROM book_editions
                WHERE book_id = :bookId
                ORDER BY id
                """.trimIndent(),
            ).setParameter("bookId", bookId)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                EditionDto(
                    id = (fields[0] as Number).toLong(),
                    title = fields[1] as String?,
                    isbn10 = fields[2] as String?,
                    isbn13 = fields[3] as String?,
                    pages = (fields[4] as Number?)?.toInt(),
                    language = fields[5] as String?,
                    publisher = fields[6] as String?,
                    format = fields[7] as String?,
                    coverUrl = fields[8] as String?,
                )
            }

    private fun fetchBookDetailsRow(bookId: Long): BookDetailsRow {
        val row =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      b.id,
                      b.slug,
                      b.title,
                      b.description,
                      b.cover_url,
                      b.release_date,
                      b.rating,
                      b.review_raw,
                      b.reviewed_at
                    FROM books b
                    WHERE b.id = :bookId
                    """.trimIndent(),
                ).setParameter("bookId", bookId)
                .singleResult as Array<*>

        return BookDetailsRow(
            bookId = (row[0] as Number).toLong(),
            slug = row[1] as String,
            title = row[2] as String,
            description = row[3] as String?,
            coverUrl = row[4] as String?,
            releaseDate = asLocalDate(row[5]),
            rating = asDouble(row[6]),
            reviewRaw = row[7] as String?,
            reviewedAt = asInstant(row[8]),
        )
    }

    private fun fetchBookDetailsRowBySlug(slug: String): BookDetailsRow {
        val row =
            entityManager
                .createNativeQuery(
                    """
                    SELECT
                      b.id,
                      b.slug,
                      b.title,
                      b.description,
                      b.cover_url,
                      b.release_date,
                      b.rating,
                      b.review_raw,
                      b.reviewed_at
                    FROM books b
                    WHERE b.slug = :slug
                    """.trimIndent(),
                ).setParameter("slug", slug)
                .resultList
                .firstOrNull() as Array<*>?
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Book not found")

        return BookDetailsRow(
            bookId = (row[0] as Number).toLong(),
            slug = row[1] as String,
            title = row[2] as String,
            description = row[3] as String?,
            coverUrl = row[4] as String?,
            releaseDate = asLocalDate(row[5]),
            rating = asDouble(row[6]),
            reviewRaw = row[7] as String?,
            reviewedAt = asInstant(row[8]),
        )
    }

    private fun resolveYearRange(year: Int): RangeDto {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val end = LocalDate.of(year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        return RangeDto(start, end)
    }

    private fun resolveSummaryRange(
        range: String,
        start: Instant?,
        end: Instant?,
    ): RangeDto =
        when (range.lowercase()) {
            "year" -> {
                val now = Instant.now().atZone(ZoneOffset.UTC)
                val startOfYear = LocalDate.of(now.year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
                val endOfYear = LocalDate.of(now.year + 1, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
                RangeDto(startOfYear, endOfYear)
            }
            "month" -> {
                val now = Instant.now().atZone(ZoneOffset.UTC)
                val yearMonth = YearMonth.of(now.year, now.month)
                val startOfMonth = yearMonth.atDay(1).atStartOfDay(ZoneOffset.UTC).toInstant()
                val endOfMonth =
                    yearMonth
                        .plusMonths(1)
                        .atDay(1)
                        .atStartOfDay(ZoneOffset.UTC)
                        .toInstant()
                RangeDto(startOfMonth, endOfMonth)
            }
            "custom" -> {
                require(start != null && end != null) { "Custom range requires start and end." }
                RangeDto(start, end)
            }
            else -> error("Unsupported range: $range")
        }

    private fun parseCursor(cursor: String?): Long? {
        if (cursor.isNullOrBlank()) return null
        val parts = cursor.split(":", limit = 2)
        require(parts.size == 2 && parts[0] == "id") { "Invalid cursor format." }
        return parts[1].toLongOrNull() ?: error("Invalid cursor value.")
    }

    private fun buildCursor(readId: Long): String = "id:$readId"

    private fun countReads(
        query: String,
        range: RangeDto,
    ): Long =
        (
            entityManager
                .createNativeQuery(query)
                .setParameter("s", range.start)
                .setParameter("e", range.end)
                .singleResult as Number
        ).toLong()

    private fun parseStatus(value: Any?): BookReadStatus {
        val name =
            when (value) {
                is String -> value
                is Enum<*> -> value.name
                else -> error("Unsupported status type: ${value?.javaClass?.name}")
            }
        return BookReadStatus.valueOf(name)
    }

    private fun asInstant(value: Any?): Instant? =
        when (value) {
            is Instant -> value
            is Timestamp -> value.toInstant()
            else -> null
        }

    private fun asLocalDate(value: Any?): LocalDate? =
        when (value) {
            is LocalDate -> value
            is Date -> value.toLocalDate()
            else -> null
        }

    private fun asDouble(value: Any?): Double? = (value as? Number)?.toDouble()

    private data class BookRow(
        val bookId: Long,
        val slug: String,
        val title: String,
        val coverUrl: String?,
        val releaseDate: LocalDate?,
        val rating: Double?,
        val reviewedAt: Instant?,
    )

    private data class BookDetailsRow(
        val bookId: Long,
        val slug: String,
        val title: String,
        val description: String?,
        val coverUrl: String?,
        val releaseDate: LocalDate?,
        val rating: Double?,
        val reviewRaw: String?,
        val reviewedAt: Instant?,
    )

    private data class ReadRow(
        val readId: Long,
        val status: BookReadStatus,
        val startedAt: Instant?,
        val finishedAt: Instant?,
        val progressPct: Double?,
        val progressPages: Int?,
        val source: String,
        val createdAt: Instant?,
        val updatedAt: Instant?,
        val bookId: Long,
        val bookTitle: String,
        val bookSlug: String,
        val bookCoverUrl: String?,
        val bookReleaseDate: LocalDate?,
        val bookRating: Double?,
        val bookReviewedAt: Instant?,
        val editionId: Long?,
        val editionTitle: String?,
        val editionIsbn10: String?,
        val editionIsbn13: String?,
        val editionPages: Int?,
        val editionLanguage: String?,
        val editionPublisher: String?,
        val editionFormat: String?,
        val editionCoverUrl: String?,
    ) {
        fun toDto(authorsByBookId: Map<Long, List<AuthorDto>>): ReadCardDto =
            ReadCardDto(
                readId = readId,
                status = status,
                startedAt = startedAt,
                finishedAt = finishedAt,
                progressPct = progressPct,
                progressPages = progressPages,
                source = source,
                book =
                    BookCardDto(
                        bookId = bookId,
                        slug = bookSlug,
                        title = bookTitle,
                        coverUrl = bookCoverUrl,
                        releaseDate = bookReleaseDate,
                        rating = bookRating,
                        reviewedAt = bookReviewedAt,
                        authors = authorsByBookId[bookId].orEmpty(),
                    ),
                edition =
                    if (editionId == null) {
                        null
                    } else {
                        EditionDto(
                            id = editionId,
                            title = editionTitle,
                            isbn10 = editionIsbn10,
                            isbn13 = editionIsbn13,
                            pages = editionPages,
                            language = editionLanguage,
                            publisher = editionPublisher,
                            format = editionFormat,
                            coverUrl = editionCoverUrl,
                        )
                    },
            )
    }
}
