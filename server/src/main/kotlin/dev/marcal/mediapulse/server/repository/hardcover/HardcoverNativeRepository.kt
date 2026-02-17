package dev.marcal.mediapulse.server.repository.hardcover

import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.util.BookSlugUtil
import jakarta.persistence.EntityManager
import jakarta.persistence.PersistenceContext
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate

@Repository
class HardcoverNativeRepository {
    @PersistenceContext
    private lateinit var em: EntityManager

    @Transactional
    fun ensureAuthorId(
        name: String,
        fingerprint: String,
    ): Long {
        val insertedId =
            em
                .createNativeQuery(
                    """
                    INSERT INTO authors(name, fingerprint)
                    VALUES (:name, :fingerprint)
                    ON CONFLICT (fingerprint) DO NOTHING
                    RETURNING id
                    """.trimIndent(),
                ).setParameter("name", name)
                .setParameter("fingerprint", fingerprint)
                .resultList
                .firstOrNull()
                ?.let { (it as Number).toLong() }

        if (insertedId != null) return insertedId

        val existingId =
            em
                .createNativeQuery("SELECT id FROM authors WHERE fingerprint = :fingerprint")
                .setParameter("fingerprint", fingerprint)
                .resultList
                .firstOrNull()
                ?.let { (it as Number).toLong() }

        return existingId ?: error("Author not found after insert attempt")
    }

    @Transactional
    fun ensureBookId(
        title: String,
        releaseDate: LocalDate?,
        description: String?,
        coverUrl: String?,
        rating: BigDecimal?,
        reviewRaw: String?,
        reviewedAt: Instant?,
        sourceUpdatedAt: Instant?,
        fingerprint: String,
    ): Long {
        val insertedId =
            em
                .createNativeQuery(
                    """
                    INSERT INTO books(title, slug, release_date, description, cover_url, rating, review_raw, reviewed_at, fingerprint)
                    VALUES (:title, :slugOnInsert, :releaseDate, :description, :coverUrl, :rating, :reviewRaw, :reviewedAt, :fingerprint)
                    ON CONFLICT (fingerprint) DO NOTHING
                    RETURNING id
                    """.trimIndent(),
                ).setParameter("title", title)
                .setParameter("slugOnInsert", fingerprint)
                .setParameter("releaseDate", releaseDate)
                .setParameter("description", description)
                .setParameter("coverUrl", coverUrl)
                .setParameter("rating", rating)
                .setParameter("reviewRaw", reviewRaw)
                .setParameter("reviewedAt", reviewedAt)
                .setParameter("fingerprint", fingerprint)
                .resultList
                .firstOrNull()
                ?.let { (it as Number).toLong() }

        val id = insertedId ?: findIdByFingerprint("books", fingerprint)
        val slug = BookSlugUtil.from(id, title)

        em
            .createNativeQuery(
                """
                UPDATE books
                   SET release_date = COALESCE(:releaseDate, release_date),
                       description = COALESCE(:description, description),
                       cover_url = COALESCE(:coverUrl, cover_url),
                       slug = :slug,
                       rating = :rating,
                       review_raw = :reviewRaw,
                       reviewed_at = :reviewedAt,
                       updated_at = COALESCE(CAST(:sourceUpdatedAt AS TIMESTAMPTZ), updated_at, NOW())
                 WHERE id = :id
                   AND (
                        CAST(:sourceUpdatedAt AS TIMESTAMPTZ) IS NULL
                        OR updated_at IS NULL
                        OR updated_at <= CAST(:sourceUpdatedAt AS TIMESTAMPTZ)
                   )
                """.trimIndent(),
            ).setParameter("releaseDate", releaseDate)
            .setParameter("description", description)
            .setParameter("coverUrl", coverUrl)
            .setParameter("slug", slug)
            .setParameter("rating", rating)
            .setParameter("reviewRaw", reviewRaw)
            .setParameter("reviewedAt", reviewedAt)
            .setParameter("sourceUpdatedAt", sourceUpdatedAt)
            .setParameter("id", id)
            .executeUpdate()

        return id
    }

    @Transactional
    fun ensureEditionId(
        bookId: Long,
        title: String?,
        isbn10: String?,
        isbn13: String?,
        pages: Int?,
        language: String?,
        publisher: String?,
        format: String?,
        coverUrl: String?,
        fingerprint: String,
    ): Long {
        val insertedId =
            em
                .createNativeQuery(
                    """
                    INSERT INTO book_editions(book_id, title, isbn_10, isbn_13, pages, language, publisher, format, cover_url, fingerprint)
                    VALUES (:bookId, :title, :isbn10, :isbn13, :pages, :language, :publisher, :format, :coverUrl, :fingerprint)
                    ON CONFLICT (fingerprint) DO NOTHING
                    RETURNING id
                    """.trimIndent(),
                ).setParameter("bookId", bookId)
                .setParameter("title", title)
                .setParameter("isbn10", isbn10)
                .setParameter("isbn13", isbn13)
                .setParameter("pages", pages)
                .setParameter("language", language)
                .setParameter("publisher", publisher)
                .setParameter("format", format)
                .setParameter("coverUrl", coverUrl)
                .setParameter("fingerprint", fingerprint)
                .resultList
                .firstOrNull()
                ?.let { (it as Number).toLong() }

        val id = insertedId ?: findIdByFingerprint("book_editions", fingerprint)

        em
            .createNativeQuery(
                """
                UPDATE book_editions
                   SET title = COALESCE(:title, title),
                       isbn_10 = COALESCE(:isbn10, isbn_10),
                       isbn_13 = COALESCE(:isbn13, isbn_13),
                       pages = COALESCE(:pages, pages),
                       language = COALESCE(:language, language),
                       publisher = COALESCE(:publisher, publisher),
                       format = COALESCE(:format, format),
                       cover_url = COALESCE(:coverUrl, cover_url),
                       updated_at = NOW()
                 WHERE id = :id
                """.trimIndent(),
            ).setParameter("title", title)
            .setParameter("isbn10", isbn10)
            .setParameter("isbn13", isbn13)
            .setParameter("pages", pages)
            .setParameter("language", language)
            .setParameter("publisher", publisher)
            .setParameter("format", format)
            .setParameter("coverUrl", coverUrl)
            .setParameter("id", id)
            .executeUpdate()

        return id
    }

    @Transactional
    fun updateBookCover(
        bookId: Long,
        coverUrl: String,
    ) {
        em
            .createNativeQuery(
                """
                UPDATE books
                   SET cover_url = :coverUrl,
                       updated_at = NOW()
                 WHERE id = :id
                """.trimIndent(),
            ).setParameter("coverUrl", coverUrl)
            .setParameter("id", bookId)
            .executeUpdate()
    }

    @Transactional
    fun updateEditionCover(
        editionId: Long,
        coverUrl: String,
    ) {
        em
            .createNativeQuery(
                """
                UPDATE book_editions
                   SET cover_url = :coverUrl,
                       updated_at = NOW()
                 WHERE id = :id
                """.trimIndent(),
            ).setParameter("coverUrl", coverUrl)
            .setParameter("id", editionId)
            .executeUpdate()
    }

    @Transactional
    fun upsertBookRead(
        bookId: Long,
        editionId: Long?,
        source: String,
        sourceEventId: Long,
        status: String,
        startedAt: Instant?,
        finishedAt: Instant?,
        progressPct: BigDecimal?,
        progressPages: Int?,
        updatedAt: Instant?,
    ): Long? =
        em
            .createNativeQuery(
                """
                INSERT INTO book_reads(
                    book_id, edition_id, source, source_event_id, status,
                    started_at, finished_at, progress_pct, progress_pages,
                    updated_at
                )
                VALUES (
                    :bookId, :editionId, :source, :sourceEventId, :status,
                    :startedAt, :finishedAt, :progressPct, :progressPages,
                    :updatedAt
                )
                ON CONFLICT (source, source_event_id)
                DO UPDATE SET
                    book_id = EXCLUDED.book_id,
                    edition_id = EXCLUDED.edition_id,
                    status = EXCLUDED.status,
                    started_at = EXCLUDED.started_at,
                    finished_at = EXCLUDED.finished_at,
                    progress_pct = EXCLUDED.progress_pct,
                    progress_pages = EXCLUDED.progress_pages,
                    updated_at = EXCLUDED.updated_at
                WHERE
                    book_reads.updated_at IS NULL
                    OR EXCLUDED.updated_at IS NULL
                    OR book_reads.updated_at <= EXCLUDED.updated_at
                RETURNING id
                """.trimIndent(),
            ).setParameter("bookId", bookId)
            .setParameter("editionId", editionId)
            .setParameter("source", source)
            .setParameter("sourceEventId", sourceEventId)
            .setParameter("status", status)
            .setParameter("startedAt", startedAt)
            .setParameter("finishedAt", finishedAt)
            .setParameter("progressPct", progressPct)
            .setParameter("progressPages", progressPages)
            .setParameter("updatedAt", updatedAt)
            .resultList
            .firstOrNull()
            ?.let { (it as Number).toLong() }

    @Transactional
    fun insertExternalIdentifier(
        entityType: EntityType,
        entityId: Long,
        provider: Provider,
        externalId: String,
    ) {
        em
            .createNativeQuery(
                """
                INSERT INTO external_identifiers(entity_type, entity_id, provider, external_id)
                VALUES (:entityType, :entityId, :provider, :externalId)
                ON CONFLICT (provider, external_id) DO NOTHING
                """.trimIndent(),
            ).setParameter("entityType", entityType.name)
            .setParameter("entityId", entityId)
            .setParameter("provider", provider.name)
            .setParameter("externalId", externalId)
            .executeUpdate()
    }

    @Transactional
    fun linkBookAuthor(
        bookId: Long,
        authorId: Long,
        role: String = "AUTHOR",
    ) {
        em
            .createNativeQuery(
                """
                INSERT INTO book_authors(book_id, author_id, role)
                VALUES (:bookId, :authorId, :role)
                ON CONFLICT DO NOTHING
                """.trimIndent(),
            ).setParameter("bookId", bookId)
            .setParameter("authorId", authorId)
            .setParameter("role", role)
            .executeUpdate()
    }

    private fun findIdByFingerprint(
        table: String,
        fingerprint: String,
    ): Long {
        val id =
            em
                .createNativeQuery("SELECT id FROM $table WHERE fingerprint = :fingerprint")
                .setParameter("fingerprint", fingerprint)
                .resultList
                .firstOrNull()
                ?.let { (it as Number).toLong() }

        return id ?: error("Row not found in $table for fingerprint")
    }
}
