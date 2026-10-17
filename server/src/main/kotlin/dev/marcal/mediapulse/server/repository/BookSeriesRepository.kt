package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.books.AuthorDto
import dev.marcal.mediapulse.server.api.books.BookGroupDto
import dev.marcal.mediapulse.server.api.books.BookGroupMemberDto
import dev.marcal.mediapulse.server.api.books.BookSeriesMetadataDto
import dev.marcal.mediapulse.server.api.books.BookSeriesMetadataField
import dev.marcal.mediapulse.server.api.books.BookSeriesProviderAuthorDto
import dev.marcal.mediapulse.server.api.books.BookSeriesSuggestionDto
import dev.marcal.mediapulse.server.model.book.BookGroupKind
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class BookSeriesRepository(
    private val jdbc: JdbcTemplate,
) {
    fun list(kind: BookGroupKind): List<BookGroupDto> =
        jdbc
            .query(
                "SELECT g.id, g.name, count(m.${kind.childColumn}) AS total FROM ${kind.table} g LEFT JOIN ${kind.members} m ON m.${kind.parentColumn} = g.id GROUP BY g.id ORDER BY g.name, g.id",
                { rs, _ -> BookGroupDto(rs.getLong("id"), rs.getString("name"), rs.getInt("total")) },
            ).map { decorate(kind, it) }

    fun summary(
        kind: BookGroupKind,
        id: Long,
    ): BookGroupDto =
        decorate(
            kind,
            jdbc.queryForObject(
                "SELECT g.id, g.name, (SELECT count(*) FROM ${kind.members} m WHERE m.${kind.parentColumn} = g.id) AS total FROM ${kind.table} g WHERE g.id = ?",
                { rs, _ -> BookGroupDto(rs.getLong("id"), rs.getString("name"), rs.getInt("total")) },
                id,
            )!!,
        )

    private fun decorate(
        kind: BookGroupKind,
        group: BookGroupDto,
    ): BookGroupDto {
        val base = group.copy(coverUrls = covers(kind, group.id))
        if (kind != BookGroupKind.SERIES) return base
        return base.copy(
            authors = authors(group.id),
            description = jdbc.queryForObject("SELECT description FROM book_series WHERE id = ?", String::class.java, group.id),
            hardcoverId = seriesHardcoverId(group.id),
        )
    }

    fun seriesHardcoverId(id: Long): Long? = jdbc.queryForObject("SELECT hardcover_id FROM book_series WHERE id = ?", Long::class.java, id)

    fun authors(id: Long): List<AuthorDto> =
        jdbc.query(
            """
            SELECT DISTINCT a.id, a.name FROM authors a
            JOIN book_authors ba ON ba.author_id = a.id AND ba.role = 'AUTHOR'
            JOIN book_series_items si ON si.book_id = ba.book_id WHERE si.series_id = ?
            ORDER BY a.name, a.id
            """.trimIndent(),
            { rs, _ -> AuthorDto(rs.getLong("id"), rs.getString("name")) },
            id,
        )

    fun forAuthor(authorId: Long): List<BookGroupDto> =
        jdbc
            .query(
                """
                SELECT s.id, s.name, (SELECT count(*) FROM book_series_items x WHERE x.series_id = s.id) AS total
                FROM book_series s WHERE EXISTS (
                    SELECT 1 FROM book_series_items si JOIN book_authors ba ON ba.book_id = si.book_id
                    WHERE si.series_id = s.id AND ba.author_id = ? AND ba.role = 'AUTHOR'
                ) ORDER BY s.name, s.id
                """.trimIndent(),
                { rs, _ -> BookGroupDto(rs.getLong("id"), rs.getString("name"), rs.getInt("total")) },
                authorId,
            ).map { decorate(BookGroupKind.SERIES, it) }

    private fun covers(
        kind: BookGroupKind,
        id: Long,
    ): List<String> {
        val membership =
            if (kind ==
                BookGroupKind.SERIES
            ) {
                "book_series_items si"
            } else {
                "book_universe_series us JOIN book_series_items si ON si.series_id = us.series_id"
            }
        val condition = if (kind == BookGroupKind.SERIES) "si.series_id = ?" else "us.universe_id = ?"
        val order = if (kind == BookGroupKind.SERIES) "si.position, b.id" else "us.position, si.position, b.id"
        return jdbc
            .query(
                """
                SELECT coalesce(b.cover_url, (SELECT e.cover_url FROM book_editions e WHERE e.book_id = b.id AND e.cover_url IS NOT NULL ORDER BY e.id LIMIT 1)) AS cover
                FROM $membership JOIN books b ON b.id = si.book_id WHERE $condition ORDER BY $order
                """.trimIndent(),
                { rs, _ -> rs.getString("cover") },
                id,
            ).filterNotNull()
            .distinct()
            .take(4)
    }

    fun metadata(id: Long): BookSeriesMetadataDto =
        jdbc.queryForObject(
            "SELECT * FROM book_series WHERE id = ?",
            { rs, _ ->
                BookSeriesMetadataDto(
                    slug = rs.getString("hardcover_slug"),
                    primaryAuthor =
                        rs.getObject("hardcover_author_id")?.let {
                            BookSeriesProviderAuthorDto((it as Number).toLong(), rs.getString("hardcover_author_name"))
                        },
                    isCompleted = rs.getObject("hardcover_is_completed") as Boolean?,
                    booksCount = (rs.getObject("hardcover_books_count") as Number?)?.toInt(),
                    primaryBooksCount = (rs.getObject("hardcover_primary_books_count") as Number?)?.toInt(),
                )
            },
            id,
        )!!

    fun description(
        id: Long,
        value: String?,
    ) {
        jdbc.update("UPDATE book_series SET description = ? WHERE id = ?", value, id)
    }

    fun applyMetadata(
        id: Long,
        source: BookSeriesSuggestionDto,
        fields: Set<BookSeriesMetadataField>,
    ) {
        if (BookSeriesMetadataField.DESCRIPTION in fields) description(id, source.description)
        if (BookSeriesMetadataField.PRIMARY_AUTHOR in
            fields
        ) {
            jdbc.update(
                "UPDATE book_series SET hardcover_author_id = ?, hardcover_author_name = ? WHERE id = ?",
                source.hardcover.primaryAuthor?.id,
                source.hardcover.primaryAuthor?.name,
                id,
            )
        }
        if (BookSeriesMetadataField.PUBLICATION_STATUS in
            fields
        ) {
            jdbc.update("UPDATE book_series SET hardcover_is_completed = ? WHERE id = ?", source.hardcover.isCompleted, id)
        }
        if (BookSeriesMetadataField.COUNTS in
            fields
        ) {
            jdbc.update(
                "UPDATE book_series SET hardcover_books_count = ?, hardcover_primary_books_count = ? WHERE id = ?",
                source.hardcover.booksCount,
                source.hardcover.primaryBooksCount,
                id,
            )
        }
        if (BookSeriesMetadataField.EXTERNAL_SLUG in
            fields
        ) {
            jdbc.update("UPDATE book_series SET hardcover_slug = ? WHERE id = ?", source.hardcover.slug, id)
        }
    }

    fun name(
        kind: BookGroupKind,
        id: Long,
        lock: Boolean = false,
    ): String? =
        jdbc
            .query(
                "SELECT name FROM ${kind.table} WHERE id = ?" + if (lock) " FOR UPDATE" else "",
                { rs, _ -> rs.getString(1) },
                id,
            ).firstOrNull()

    fun create(
        kind: BookGroupKind,
        name: String,
    ): Long = jdbc.queryForObject("INSERT INTO ${kind.table}(name) VALUES (?) RETURNING id", Long::class.java, name)!!

    fun rename(
        kind: BookGroupKind,
        id: Long,
        name: String,
    ) {
        jdbc.update("UPDATE ${kind.table} SET name = ? WHERE id = ?", name, id)
    }

    fun delete(
        kind: BookGroupKind,
        id: Long,
    ) {
        jdbc.update("DELETE FROM ${kind.table} WHERE id = ?", id)
    }

    fun childExists(
        kind: BookGroupKind,
        id: Long,
    ): Boolean = jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM ${kind.childTable} WHERE id = ?)", Boolean::class.java, id)!!

    fun bookExists(id: Long): Boolean = childExists(BookGroupKind.SERIES, id)

    fun hardcoverId(id: Long): Long? =
        jdbc
            .query("SELECT hardcover_id FROM books WHERE id = ?", { rs, _ ->
                rs.getObject(1)?.let {
                    (it as Number).toLong()
                }
            }, id)
            .firstOrNull()

    fun items(
        kind: BookGroupKind,
        id: Long,
    ): List<BookGroupMemberDto> {
        val book = kind == BookGroupKind.SERIES
        return jdbc
            .query(
                "SELECT c.id, c.${if (book) "title" else "name"} AS name, m.position, ${if (book) "c.slug, coalesce(c.cover_url, (SELECT e.cover_url FROM book_editions e WHERE e.book_id = c.id AND e.cover_url IS NOT NULL ORDER BY e.id LIMIT 1)) AS cover_url" else "NULL AS slug, NULL AS cover_url"} FROM ${kind.members} m JOIN ${kind.childTable} c ON c.id = m.${kind.childColumn} WHERE m.${kind.parentColumn} = ? ORDER BY m.position, c.id",
                {
                    rs,
                    _,
                    ->
                    BookGroupMemberDto(
                        rs.getLong("id"),
                        rs.getString("name"),
                        rs.getInt("position"),
                        rs.getString("slug"),
                        rs.getString("cover_url"),
                    )
                },
                id,
            ).map { if (book) it else it.copy(coverUrl = covers(BookGroupKind.SERIES, it.id).firstOrNull()) }
    }

    fun parents(bookId: Long): List<BookGroupDto> =
        jdbc
            .query(
                """
                SELECT s.id, s.name, (SELECT count(*) FROM book_series_items x WHERE x.series_id = s.id) AS total
                FROM book_series s
                JOIN book_series_items membership ON membership.series_id = s.id
                WHERE membership.book_id = ?
                ORDER BY s.name, s.id
                """.trimIndent(),
                { rs, _ -> BookGroupDto(rs.getLong("id"), rs.getString("name"), rs.getInt("total")) },
                bookId,
            ).map { decorate(BookGroupKind.SERIES, it) }

    fun universes(seriesId: Long): List<BookGroupDto> =
        jdbc
            .query(
                "SELECT u.id, u.name, (SELECT count(*) FROM book_universe_series x WHERE x.universe_id = u.id) AS total FROM book_universes u JOIN book_universe_series m ON m.universe_id = u.id WHERE m.series_id = ? ORDER BY u.name, u.id",
                { rs, _ -> BookGroupDto(rs.getLong("id"), rs.getString("name"), rs.getInt("total")) },
                seriesId,
            ).map { decorate(BookGroupKind.UNIVERSES, it) }

    fun add(
        kind: BookGroupKind,
        id: Long,
        childId: Long,
    ) {
        jdbc.update(
            "INSERT INTO ${kind.members}(${kind.parentColumn}, ${kind.childColumn}, position) SELECT ?, ?, coalesce(max(position), 0) + 1 FROM ${kind.members} WHERE ${kind.parentColumn} = ? ON CONFLICT DO NOTHING",
            id,
            childId,
            id,
        )
    }

    fun remove(
        kind: BookGroupKind,
        id: Long,
        childId: Long,
    ) {
        jdbc.update("DELETE FROM ${kind.members} WHERE ${kind.parentColumn} = ? AND ${kind.childColumn} = ?", id, childId)
    }

    fun position(
        kind: BookGroupKind,
        id: Long,
        childId: Long,
        position: Int,
    ) {
        jdbc.update(
            "UPDATE ${kind.members} SET position = ? WHERE ${kind.parentColumn} = ? AND ${kind.childColumn} = ?",
            position,
            id,
            childId,
        )
    }

    fun markSource(
        id: Long,
        hardcoverId: Long,
    ) {
        jdbc.update("UPDATE book_series SET hardcover_id = coalesce(hardcover_id, ?) WHERE id = ?", hardcoverId, id)
    }

    fun localBooks(
        hardcoverId: Long,
        position: Int,
    ): List<BookGroupMemberDto> =
        jdbc.query(
            "SELECT b.id, b.title, b.slug, coalesce(b.cover_url, (SELECT e.cover_url FROM book_editions e WHERE e.book_id = b.id AND e.cover_url IS NOT NULL ORDER BY e.id LIMIT 1)) AS cover_url FROM books b WHERE b.hardcover_id = ? ORDER BY b.id",
            {
                rs,
                _,
                ->
                BookGroupMemberDto(rs.getLong("id"), rs.getString("title"), position, rs.getString("slug"), rs.getString("cover_url"))
            },
            hardcoverId,
        )
}
