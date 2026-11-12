package dev.marcal.mediapulse.server.repository

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentDto
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class BookAuthorProfileRepository(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    fun find(authorId: Long): AuthorEnrichmentDto? =
        jdbc
            .query(
                "SELECT snapshot::text FROM book_author_profiles WHERE author_id = ?",
                { rs, _ -> mapper.readValue(rs.getString(1), AuthorEnrichmentDto::class.java) },
                authorId,
            ).firstOrNull()

    fun visible(authorId: Long): AuthorEnrichmentDto? =
        jdbc
            .query(
                """SELECT p.snapshot::text FROM book_author_profiles p WHERE p.author_id = ? AND (p.managed_manually OR (
        EXISTS (SELECT 1 FROM hardcover_author_candidates c WHERE c.author_id = p.author_id AND c.hardcover_id = p.hardcover_id)
        AND NOT EXISTS (SELECT 1 FROM hardcover_author_candidates c WHERE c.author_id = p.author_id AND c.hardcover_id <> p.hardcover_id)
        AND NOT EXISTS (SELECT 1 FROM hardcover_author_candidates c WHERE c.author_id <> p.author_id AND c.hardcover_id = p.hardcover_id)))""",
                { rs, _ -> mapper.readValue(rs.getString(1), AuthorEnrichmentDto::class.java) },
                authorId,
            ).firstOrNull()

    fun exists(authorId: Long): Boolean =
        jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM authors WHERE id = ?)", Boolean::class.java, authorId) == true

    fun lock(authorId: Long): Boolean =
        jdbc.query("SELECT id FROM authors WHERE id = ? FOR UPDATE", { rs, _ -> rs.getLong(1) }, authorId).isNotEmpty()

    fun owner(hardcoverId: Long): Long? =
        jdbc
            .query(
                "SELECT author_id FROM book_author_profiles WHERE hardcover_id = ?",
                { rs, _ -> rs.getLong(1) },
                hardcoverId,
            ).firstOrNull()

    fun save(
        authorId: Long,
        profile: AuthorEnrichmentDto,
    ) {
        jdbc.update(
            """INSERT INTO book_author_profiles(author_id, hardcover_id, snapshot, managed_manually) VALUES (?, ?, CAST(? AS jsonb), TRUE)
            ON CONFLICT (author_id) DO UPDATE SET hardcover_id = EXCLUDED.hardcover_id, snapshot = EXCLUDED.snapshot, managed_manually = TRUE""",
            authorId,
            profile.hardcoverId,
            mapper.writeValueAsString(profile),
        )
    }

    fun isManual(authorId: Long): Boolean =
        jdbc
            .query(
                "SELECT managed_manually FROM book_author_profiles WHERE author_id = ?",
                { rs, _ -> rs.getBoolean(1) },
                authorId,
            ).firstOrNull() == true

    fun saveAutomatically(
        authorId: Long,
        profile: AuthorEnrichmentDto,
    ) {
        jdbc.update(
            """INSERT INTO book_author_profiles(author_id, hardcover_id, snapshot, managed_manually)
            VALUES (?, ?, CAST(? AS jsonb), FALSE)
            ON CONFLICT (author_id) DO UPDATE SET snapshot = EXCLUDED.snapshot
            WHERE NOT book_author_profiles.managed_manually AND book_author_profiles.hardcover_id = EXCLUDED.hardcover_id""",
            authorId,
            profile.hardcoverId,
            mapper.writeValueAsString(profile),
        )
    }
}
