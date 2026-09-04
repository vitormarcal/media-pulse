package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class ShowTermsCrudRepository(
    private val entityManager: EntityManager,
) {
    data class Candidate(
        val showId: Long,
        val tmdbId: String,
    )

    fun candidates(limit: Int): List<Candidate> =
        entityManager
            .createNativeQuery(
                """
                SELECT id, tmdb_id FROM tv_shows
                WHERE terms_synced_at IS NULL AND tmdb_id IS NOT NULL
                  AND (terms_sync_attempted_at IS NULL OR terms_sync_attempted_at <= NOW() - INTERVAL '1 day')
                ORDER BY terms_sync_attempted_at NULLS FIRST, id
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("limit", limit.coerceIn(1, 1000))
            .resultList
            .map { row ->
                val fields = row as Array<*>
                Candidate((fields[0] as Number).toLong(), fields[1] as String)
            }

    fun markSuccess(showId: Long): Int = update(showId, null, true)

    fun markFailure(
        showId: Long,
        error: String,
    ): Int = update(showId, error.take(2000), false)

    private fun update(
        showId: Long,
        error: String?,
        success: Boolean,
    ): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE tv_shows SET
                  terms_synced_at = CASE WHEN :success THEN NOW() ELSE terms_synced_at END,
                  terms_sync_attempted_at = NOW(), terms_sync_error = :error, updated_at = NOW()
                WHERE id = :showId
                """.trimIndent(),
            ).setParameter("success", success)
            .setParameter("error", error)
            .setParameter("showId", showId)
            .executeUpdate()
}
