package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.books.BookGroupDto
import dev.marcal.mediapulse.server.api.books.BookGroupMemberDto
import dev.marcal.mediapulse.server.model.book.BookGroupKind
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class BookSeriesRepository(
    private val jdbc: JdbcTemplate,
) {
    fun list(kind: BookGroupKind): List<BookGroupDto> =
        jdbc.query(
            "SELECT g.id, g.name, count(m.${kind.childColumn}) AS total FROM ${kind.table} g LEFT JOIN ${kind.members} m ON m.${kind.parentColumn} = g.id GROUP BY g.id ORDER BY g.name, g.id",
            { rs, _ -> BookGroupDto(rs.getLong("id"), rs.getString("name"), rs.getInt("total")) },
        )

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
        return jdbc.query(
            "SELECT c.id, c.${if (book) "title" else "name"} AS name, m.position, ${if (book) "c.slug, c.cover_url" else "NULL AS slug, NULL AS cover_url"} FROM ${kind.members} m JOIN ${kind.childTable} c ON c.id = m.${kind.childColumn} WHERE m.${kind.parentColumn} = ? ORDER BY m.position, c.id",
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
        )
    }

    fun parents(bookId: Long): List<BookGroupDto> =
        jdbc.query(
            """
            SELECT s.id, s.name, (SELECT count(*) FROM book_series_items x WHERE x.series_id = s.id) AS total
            FROM book_series s
            JOIN book_series_items membership ON membership.series_id = s.id
            WHERE membership.book_id = ?
            ORDER BY s.name, s.id
            """.trimIndent(),
            { rs, _ -> BookGroupDto(rs.getLong("id"), rs.getString("name"), rs.getInt("total")) },
            bookId,
        )

    fun universes(seriesId: Long): List<BookGroupDto> =
        jdbc.query(
            "SELECT u.id, u.name, (SELECT count(*) FROM book_universe_series x WHERE x.universe_id = u.id) AS total FROM book_universes u JOIN book_universe_series m ON m.universe_id = u.id WHERE m.series_id = ? ORDER BY u.name, u.id",
            { rs, _ -> BookGroupDto(rs.getLong("id"), rs.getString("name"), rs.getInt("total")) },
            seriesId,
        )

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
            "SELECT id, title, slug, cover_url FROM books WHERE hardcover_id = ? ORDER BY id",
            {
                rs,
                _,
                ->
                BookGroupMemberDto(rs.getLong("id"), rs.getString("title"), position, rs.getString("slug"), rs.getString("cover_url"))
            },
            hardcoverId,
        )
}
