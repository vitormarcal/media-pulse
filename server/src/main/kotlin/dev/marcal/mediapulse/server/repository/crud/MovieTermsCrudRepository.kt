package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class MovieTermsCrudRepository(
    private val entityManager: EntityManager,
) {
    data class MovieTermsSyncCandidate(
        val movieId: Long,
        val tmdbId: String,
    )

    fun findTmdbSyncCandidates(limit: Int): List<MovieTermsSyncCandidate> =
        entityManager
            .createNativeQuery(
                """
                SELECT m.id, m.tmdb_id
                FROM movies m
                WHERE m.terms_synced_at IS NULL
                  AND m.tmdb_id IS NOT NULL
                  AND (m.terms_sync_attempted_at IS NULL OR m.terms_sync_attempted_at <= NOW() - INTERVAL '1 day')
                ORDER BY m.terms_sync_attempted_at NULLS FIRST, m.id ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("limit", limit.coerceAtLeast(1))
            .resultList
            .map { row ->
                val fields = row as Array<*>
                MovieTermsSyncCandidate(
                    movieId = (fields[0] as Number).toLong(),
                    tmdbId = fields[1] as String,
                )
            }

    fun countPendingTmdbSyncCandidates(): Long =
        (
            entityManager
                .createNativeQuery(
                    """
                    SELECT COUNT(*)
                    FROM movies m
                    WHERE m.terms_synced_at IS NULL
                      AND m.tmdb_id IS NOT NULL
                      AND (m.terms_sync_attempted_at IS NULL OR m.terms_sync_attempted_at <= NOW() - INTERVAL '1 day')
                    """.trimIndent(),
                ).singleResult as Number
        ).toLong()

    fun markTermsSynced(movieId: Long): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE movies
                SET terms_synced_at = NOW(),
                    terms_sync_attempted_at = NOW(),
                    terms_sync_error = NULL,
                    updated_at = NOW()
                WHERE id = :movieId
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .executeUpdate()
}
