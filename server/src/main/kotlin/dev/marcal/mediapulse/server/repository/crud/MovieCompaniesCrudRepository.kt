package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class MovieCompaniesCrudRepository(
    private val entityManager: EntityManager,
) {
    data class MovieCompaniesSyncCandidate(
        val movieId: Long,
        val tmdbId: String,
    )

    fun findTmdbSyncCandidates(limit: Int): List<MovieCompaniesSyncCandidate> =
        entityManager
            .createNativeQuery(
                """
                SELECT m.id, m.tmdb_id
                FROM movies m
                WHERE m.companies_synced_at IS NULL
                  AND m.tmdb_id IS NOT NULL
                ORDER BY m.id ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("limit", limit.coerceAtLeast(1))
            .resultList
            .map { row ->
                val fields = row as Array<*>
                MovieCompaniesSyncCandidate(
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
                    WHERE m.companies_synced_at IS NULL
                      AND m.tmdb_id IS NOT NULL
                    """.trimIndent(),
                ).singleResult as Number
        ).toInt()

    fun markCompaniesSynced(movieId: Long): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE movies
                SET companies_synced_at = NOW(),
                    updated_at = NOW()
                WHERE id = :movieId
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .executeUpdate()
}
