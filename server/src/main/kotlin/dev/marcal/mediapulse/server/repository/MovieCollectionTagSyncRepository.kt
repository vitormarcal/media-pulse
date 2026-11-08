package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.movies.MovieCollectionTagSyncDto
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.movie.MovieCollectionTagSnapshot
import dev.marcal.mediapulse.server.model.movie.MovieCollectionTagTarget
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
class MovieCollectionTagSyncRepository(
    private val jdbc: JdbcTemplate,
) {
    fun state(id: Long): MovieCollectionTagSyncDto? =
        jdbc
            .query(
                "SELECT collection_tags_revision, collection_tags_pending, collection_tags_message FROM movies WHERE id = ?",
                { rs, _ ->
                    val remote = targets(id).isNotEmpty()
                    MovieCollectionTagSyncDto(rs.getLong(1), remote && rs.getBoolean(2), if (remote) rs.getString(3) else null, remote)
                },
                id,
            ).firstOrNull()

    fun candidates(limit: Int): List<Long> =
        jdbc.query(
            """
            SELECT id FROM movies WHERE collection_tags_pending AND collection_tags_next_attempt_at <= now()
              AND (collection_tags_claim_until IS NULL OR collection_tags_claim_until <= now())
            ORDER BY collection_tags_next_attempt_at, id LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.getLong(1) },
            limit,
        )

    // Caller owns a short transaction. No row lock is held during network I/O.
    fun claim(id: Long): MovieCollectionTagSnapshot? {
        val token = UUID.randomUUID()
        val revision =
            jdbc
                .query(
                    """
                    UPDATE movies SET collection_tags_claim_token = ?, collection_tags_claim_until = now() + INTERVAL '5 minutes'
                    WHERE id = ? AND collection_tags_pending AND collection_tags_next_attempt_at <= now()
                      AND (collection_tags_claim_until IS NULL OR collection_tags_claim_until <= now())
                    RETURNING collection_tags_revision
                    """.trimIndent(),
                    { rs, _ -> rs.getLong(1) },
                    token,
                    id,
                ).firstOrNull() ?: return null
        val names =
            jdbc.query(
                "SELECT c.name FROM movie_collections c JOIN movies m ON m.collection_id = c.id WHERE m.id = ?",
                { rs, _ -> rs.getString(1) },
                id,
            )
        return MovieCollectionTagSnapshot(id, revision, token, names, targets(id))
    }

    fun renew(snapshot: MovieCollectionTagSnapshot): Boolean =
        jdbc.update(
            """
            UPDATE movies SET collection_tags_claim_until = now() + INTERVAL '5 minutes'
            WHERE id = ? AND collection_tags_revision = ? AND collection_tags_claim_token = ? AND collection_tags_pending
            """.trimIndent(),
            snapshot.movieId,
            snapshot.revision,
            snapshot.claimToken,
        ) == 1

    fun finish(
        snapshot: MovieCollectionTagSnapshot,
        failed: Boolean,
        message: String?,
    ): Boolean =
        jdbc.update(
            """
            UPDATE movies SET collection_tags_pending = ?, collection_tags_message = ?,
                collection_tags_next_attempt_at = now() + INTERVAL '10 minutes',
                collection_tags_claim_token = NULL, collection_tags_claim_until = NULL
            WHERE id = ? AND collection_tags_revision = ? AND collection_tags_claim_token = ?
            """.trimIndent(),
            failed,
            message,
            snapshot.movieId,
            snapshot.revision,
            snapshot.claimToken,
        ) == 1

    fun retry(id: Long) {
        jdbc.update(
            "UPDATE movies SET collection_tags_pending = true, collection_tags_message = NULL, collection_tags_next_attempt_at = now() WHERE id = ?",
            id,
        )
    }

    private fun targets(id: Long): List<MovieCollectionTagTarget> =
        jdbc
            .query(
                """
                SELECT DISTINCT 'movie' AS entity_type, m.id AS entity_id, images.url AS cover_url
                FROM movies m JOIN (
                    SELECT id AS movie_id, cover_url AS url FROM movies WHERE id = ?
                    UNION SELECT movie_id, url FROM movie_images WHERE movie_id = ?
                ) images ON images.movie_id = m.id
                WHERE images.url IS NOT NULL ORDER BY images.url
                """.trimIndent(),
                { rs, _ ->
                    VisualLibraryClient.assetId(rs.getString("cover_url").orEmpty())?.let {
                        MovieCollectionTagTarget(it, rs.getString("entity_type"), rs.getLong("entity_id"))
                    }
                },
                id,
                id,
            ).filterNotNull()
}
