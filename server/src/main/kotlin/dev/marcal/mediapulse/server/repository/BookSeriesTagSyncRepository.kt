package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.books.BookSeriesTagSyncDto
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.book.BookSeriesTagSnapshot
import dev.marcal.mediapulse.server.model.book.BookSeriesTagTarget
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class BookSeriesTagSyncRepository(
    private val jdbc: JdbcTemplate,
) {
    fun state(id: Long): BookSeriesTagSyncDto? =
        jdbc
            .query(
                "SELECT series_tags_revision, series_tags_pending, series_tags_message FROM books WHERE id = ?",
                { rs, _ ->
                    val remote = targets(id).isNotEmpty()
                    BookSeriesTagSyncDto(rs.getLong(1), remote && rs.getBoolean(2), if (remote) rs.getString(3) else null, remote)
                },
                id,
            ).firstOrNull()

    fun candidates(limit: Int): List<Long> =
        jdbc.query(
            """
            SELECT id FROM books WHERE series_tags_pending AND series_tags_next_attempt_at <= now()
              AND (series_tags_claim_until IS NULL OR series_tags_claim_until <= now())
            ORDER BY series_tags_next_attempt_at, id LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.getLong(1) },
            limit,
        )

    // Caller owns a short transaction. No row lock is held during network I/O.
    fun claim(id: Long): BookSeriesTagSnapshot? {
        val token = UUID.randomUUID()
        val revision =
            jdbc
                .query(
                    """
                    UPDATE books SET series_tags_claim_token = ?, series_tags_claim_until = now() + INTERVAL '5 minutes'
                    WHERE id = ? AND series_tags_pending AND series_tags_next_attempt_at <= now()
                      AND (series_tags_claim_until IS NULL OR series_tags_claim_until <= now())
                    RETURNING series_tags_revision
                    """.trimIndent(),
                    { rs, _ -> rs.getLong(1) },
                    token,
                    id,
                ).firstOrNull() ?: return null
        val names =
            jdbc.query(
                "SELECT s.name FROM book_series s JOIN book_series_items i ON i.series_id = s.id WHERE i.book_id = ? ORDER BY s.id",
                { rs, _ -> rs.getString(1) },
                id,
            )
        return BookSeriesTagSnapshot(id, revision, token, names, targets(id))
    }

    fun renew(snapshot: BookSeriesTagSnapshot): Boolean =
        jdbc.update(
            """
            UPDATE books SET series_tags_claim_until = now() + INTERVAL '5 minutes'
            WHERE id = ? AND series_tags_revision = ? AND series_tags_claim_token = ? AND series_tags_pending
            """.trimIndent(),
            snapshot.bookId,
            snapshot.revision,
            snapshot.claimToken,
        ) == 1

    fun finish(
        snapshot: BookSeriesTagSnapshot,
        failed: Boolean,
        message: String?,
    ): Boolean =
        jdbc.update(
            """
            UPDATE books SET series_tags_pending = ?, series_tags_message = ?,
                series_tags_next_attempt_at = now() + INTERVAL '10 minutes',
                series_tags_claim_token = NULL, series_tags_claim_until = NULL
            WHERE id = ? AND series_tags_revision = ? AND series_tags_claim_token = ?
            """.trimIndent(),
            failed,
            message,
            snapshot.bookId,
            snapshot.revision,
            snapshot.claimToken,
        ) == 1

    fun retry(id: Long) {
        jdbc.update(
            "UPDATE books SET series_tags_pending = true, series_tags_message = NULL, series_tags_next_attempt_at = now() WHERE id = ?",
            id,
        )
    }

    private fun targets(id: Long): List<BookSeriesTagTarget> =
        jdbc
            .query(
                """
                SELECT 'book' AS entity_type, id AS entity_id, cover_url FROM books WHERE id = ?
                UNION ALL
                SELECT 'book_edition', id, cover_url FROM book_editions WHERE book_id = ?
                ORDER BY entity_type, entity_id
                """.trimIndent(),
                { rs, _ ->
                    VisualLibraryClient.assetId(rs.getString("cover_url").orEmpty())?.let {
                        BookSeriesTagTarget(it, rs.getString("entity_type"), rs.getLong("entity_id"))
                    }
                },
                id,
                id,
            ).filterNotNull()
}
