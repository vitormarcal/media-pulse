package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.image.LocalImageCleanup
import dev.marcal.mediapulse.server.model.image.LocalImageReference
import dev.marcal.mediapulse.server.model.image.LocalImageTarget
import org.springframework.jdbc.core.ConnectionCallback
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class LocalImageMigrationRepository(
    private val jdbc: JdbcTemplate,
) {
    private val targets = LocalImageTarget.entries

    // Session lock prevents two application instances from migrating/deleting the same files.
    // No database transaction is held during remote I/O.
    fun withWorkerLock(block: () -> Unit) {
        jdbc.execute(
            ConnectionCallback<Unit> { connection ->
                val acquired =
                    connection.createStatement().use { statement ->
                        statement.executeQuery("SELECT pg_try_advisory_lock(716031271)").use {
                            it.next()
                            it.getBoolean(1)
                        }
                    }
                if (acquired) {
                    try {
                        block()
                    } finally {
                        connection.createStatement().use { it.execute("SELECT pg_advisory_unlock(716031271)") }
                    }
                }
            },
        )
    }

    fun candidates(limit: Int): List<String> {
        val union = targets.joinToString(" UNION ") { "SELECT ${it.column} AS url FROM ${it.table} WHERE ${it.column} LIKE '/covers/%'" }
        return jdbc.query(
            """
            SELECT images.url FROM ($union) images
            LEFT JOIN local_image_migrations m ON m.local_url = images.url
            WHERE m.attempted_at IS NULL OR m.attempted_at < NOW() - INTERVAL '10 minutes'
            ORDER BY m.attempted_at NULLS FIRST, images.url LIMIT ?
            """.trimIndent(),
            { rs, _ -> rs.getString("url") },
            limit,
        )
    }

    fun references(url: String): List<LocalImageReference> =
        targets.flatMap { target ->
            jdbc.query(
                "SELECT id, ${target.entityColumn} AS entity_id FROM ${target.table} WHERE ${target.column} = ? ORDER BY id",
                { rs, _ -> LocalImageReference(target, rs.getLong("id"), rs.getLong("entity_id")) },
                url,
            )
        }

    fun recordAttempt(url: String) {
        jdbc.update(
            """
            INSERT INTO local_image_migrations(local_url) VALUES (?)
            ON CONFLICT (local_url) DO UPDATE SET attempted_at = NOW(), remote_url = NULL, sha256 = NULL, cleaned = FALSE
            """.trimIndent(),
            url,
        )
    }

    // Used only inside a short transaction, after remote I/O has finished.
    fun lockReferences() {
        jdbc.execute("SET LOCAL lock_timeout = '2s'")
        jdbc.execute(
            "LOCK TABLE books, book_editions, magazine_issues, artwork_images, artwork_artists, artworks, games, game_images IN SHARE ROW EXCLUSIVE MODE",
        )
    }

    fun artworkRevision(id: Long): Long? =
        jdbc.query("SELECT movements_revision FROM artworks WHERE id = ?", { rs, _ -> rs.getLong(1) }, id).singleOrNull()

    fun title(reference: LocalImageReference): String? =
        when (reference.target) {
            LocalImageTarget.MAGAZINE ->
                jdbc
                    .query(
                        "SELECT p.name FROM magazine_issues i JOIN magazine_publications p ON p.id = i.publication_id WHERE i.id = ?",
                        { rs, _ -> rs.getString(1) },
                        reference.entityId,
                    ).singleOrNull()
            LocalImageTarget.GAME_COVER, LocalImageTarget.GAME_IMAGE ->
                jdbc.query("SELECT title FROM games WHERE id = ?", { rs, _ -> rs.getString(1) }, reference.entityId).singleOrNull()
            LocalImageTarget.BOOK, LocalImageTarget.BOOK_EDITION ->
                jdbc
                    .query(
                        "SELECT title FROM ${reference.target.table} WHERE id = ?",
                        { rs, _ -> rs.getString(1) },
                        reference.entityId,
                    ).singleOrNull()
            else -> null
        }

    fun replace(
        reference: LocalImageReference,
        oldUrl: String,
        newUrl: String,
    ) {
        val target = reference.target
        if (target == LocalImageTarget.GAME_IMAGE) mergeGameDuplicate(reference, newUrl)
        check(
            jdbc.update(
                "UPDATE ${target.table} SET ${target.column} = ? WHERE id = ? AND ${target.column} = ?",
                newUrl,
                reference.rowId,
                oldUrl,
            ) == 1,
        ) { "Vínculo de imagem alterado durante a migração." }
    }

    private fun mergeGameDuplicate(
        reference: LocalImageReference,
        newUrl: String,
    ) {
        val primary =
            jdbc
                .query(
                    "SELECT is_primary FROM game_images WHERE game_id = ? AND url = ? AND id <> ?",
                    { rs, _ -> rs.getBoolean(1) },
                    reference.entityId,
                    newUrl,
                    reference.rowId,
                ).singleOrNull() ?: return
        jdbc.update("DELETE FROM game_images WHERE game_id = ? AND url = ? AND id <> ?", reference.entityId, newUrl, reference.rowId)
        if (primary) jdbc.update("UPDATE game_images SET is_primary = TRUE WHERE id = ?", reference.rowId)
    }

    fun recordMigrated(
        url: String,
        remoteUrl: String,
        hash: String,
    ) {
        check(
            jdbc.update(
                "UPDATE local_image_migrations SET remote_url = ?, sha256 = ?, cleaned = FALSE WHERE local_url = ?",
                remoteUrl,
                hash,
                url,
            ) == 1,
        )
    }

    fun pendingCleanup(limit: Int): List<LocalImageCleanup> =
        jdbc.query(
            """
            SELECT local_url, remote_url, sha256 FROM local_image_migrations
            WHERE remote_url IS NOT NULL AND NOT cleaned ORDER BY attempted_at, local_url LIMIT ?
            """.trimIndent(),
            { rs, _ -> LocalImageCleanup(rs.getString(1), rs.getString(2), rs.getString(3)) },
            limit,
        )

    fun cleanupAttempt(url: String) {
        jdbc.update("UPDATE local_image_migrations SET attempted_at = NOW() WHERE local_url = ?", url)
    }

    fun markCleaned(url: String) {
        jdbc.update("UPDATE local_image_migrations SET cleaned = TRUE WHERE local_url = ?", url)
    }

    // Protect references outside the migration scope too. These columns are read-only here.
    private val referenceColumns =
        targets.map { it.table to it.column } +
            listOf(
                "albums" to "cover_url",
                "artists" to "profile_image_url",
                "movies" to "cover_url",
                "movie_images" to "url",
                "tv_shows" to "cover_url",
                "tv_show_images" to "url",
                "workouts" to "photo_url",
                "people" to "profile_url",
                "movie_companies" to "logo_url",
                "movie_collections" to "poster_url",
                "movie_collections" to "backdrop_url",
                "movie_collection_members" to "poster_url",
                "movie_collection_members" to "backdrop_url",
                "movie_company_members" to "poster_url",
                "person_filmography_members" to "poster_url",
                "person_filmography_members" to "backdrop_url",
            )

    fun lockReferencesForCleanup() {
        jdbc.execute("SET LOCAL lock_timeout = '2s'")
        val tables = (referenceColumns.map { it.first } + "artworks").distinct().sorted().joinToString(", ")
        jdbc.execute("LOCK TABLE $tables IN SHARE ROW EXCLUSIVE MODE")
    }

    fun hasLocalReferences(url: String): Boolean =
        referenceColumns.any { (table, column) ->
            jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM $table WHERE $column = ?)", Boolean::class.java, url) == true
        }
}
