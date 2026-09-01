package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class MovieCreditsCrudRepository(
    private val entityManager: EntityManager,
) {
    data class MovieCreditsSyncCandidate(
        val movieId: Long,
        val tmdbId: String,
    )

    fun findTmdbSyncCandidates(limit: Int): List<MovieCreditsSyncCandidate> =
        entityManager
            .createNativeQuery(
                """
                SELECT m.id, m.tmdb_id
                FROM movies m
                WHERE m.credits_synced_at IS NULL
                  AND m.tmdb_id IS NOT NULL
                ORDER BY m.id ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("limit", limit.coerceAtLeast(1))
            .resultList
            .map { row ->
                val fields = row as Array<*>
                MovieCreditsSyncCandidate(
                    movieId = (fields[0] as Number).toLong(),
                    tmdbId = fields[1] as String,
                )
            }

    fun countPendingTmdbSyncCandidates(): Int =
        (
            entityManager
                .createNativeQuery(
                    """
                    SELECT COUNT(*)
                    FROM movies m
                    WHERE m.credits_synced_at IS NULL
                      AND m.tmdb_id IS NOT NULL
                    """.trimIndent(),
                ).singleResult as Number
        ).toInt()

    fun markCreditsSynced(movieId: Long): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE movies
                SET credits_synced_at = NOW(),
                    updated_at = NOW()
                WHERE id = :movieId
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .executeUpdate()
}
