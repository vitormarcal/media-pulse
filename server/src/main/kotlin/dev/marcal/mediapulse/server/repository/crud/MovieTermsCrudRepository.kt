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
                SELECT m.id, ei.external_id
                FROM movies m
                JOIN external_identifiers ei
                  ON ei.entity_type = 'MOVIE'
                 AND ei.entity_id = m.id
                 AND ei.provider = 'TMDB'
                WHERE m.terms_synced_at IS NULL
                ORDER BY m.id ASC
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
                    JOIN external_identifiers ei
                      ON ei.entity_type = 'MOVIE'
                     AND ei.entity_id = m.id
                     AND ei.provider = 'TMDB'
                    WHERE m.terms_synced_at IS NULL
                    """.trimIndent(),
                ).singleResult as Number
        ).toLong()

    fun markTermsSynced(movieId: Long): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE movies
                SET terms_synced_at = NOW(),
                    updated_at = NOW()
                WHERE id = :movieId
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .executeUpdate()
}
