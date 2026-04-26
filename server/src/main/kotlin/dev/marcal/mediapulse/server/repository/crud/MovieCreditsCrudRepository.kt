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
                SELECT m.id, ei.external_id
                FROM movies m
                JOIN external_identifiers ei
                  ON ei.entity_type = 'MOVIE'
                 AND ei.entity_id = m.id
                 AND ei.provider = 'TMDB'
                WHERE m.credits_synced_at IS NULL
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
                    JOIN external_identifiers ei
                      ON ei.entity_type = 'MOVIE'
                     AND ei.entity_id = m.id
                     AND ei.provider = 'TMDB'
                    WHERE m.credits_synced_at IS NULL
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

    data class LocalMovieByTmdbId(
        val tmdbId: String,
        val movieId: Long,
        val slug: String?,
    )

    fun findLocalMoviesByTmdbIds(tmdbIds: List<String>): Map<String, LocalMovieByTmdbId> {
        val normalizedIds = tmdbIds.mapNotNull { it.trim().ifBlank { null } }.distinct()
        if (normalizedIds.isEmpty()) return emptyMap()

        return entityManager
            .createNativeQuery(
                """
                SELECT ei.external_id, m.id, m.slug
                FROM external_identifiers ei
                JOIN movies m ON m.id = ei.entity_id
                WHERE ei.entity_type = 'MOVIE'
                  AND ei.provider = 'TMDB'
                  AND ei.external_id IN (:tmdbIds)
                """.trimIndent(),
            ).setParameter("tmdbIds", normalizedIds)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                LocalMovieByTmdbId(
                    tmdbId = fields[0] as String,
                    movieId = (fields[1] as Number).toLong(),
                    slug = fields[2] as String?,
                )
            }.associateBy { it.tmdbId }
    }
}
