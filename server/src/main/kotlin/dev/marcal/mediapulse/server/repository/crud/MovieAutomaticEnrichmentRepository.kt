package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class MovieAutomaticEnrichmentRepository(
    private val entityManager: EntityManager,
) {
    fun findPendingMovieIds(limit: Int): List<Long> =
        entityManager
            .createNativeQuery(
                """
                SELECT m.id
                FROM movies m
                WHERE (m.tmdb_id IS NULL AND m.imdb_id IS NOT NULL AND (
                        m.tmdb_resolution_checked_at IS NULL
                        OR m.tmdb_resolution_checked_at < NOW() - INTERVAL '1 day'
                      ))
                   OR (m.tmdb_id IS NOT NULL AND (
                        m.terms_synced_at IS NULL
                        OR m.credits_synced_at IS NULL
                        OR m.companies_synced_at IS NULL
                   ))
                ORDER BY CASE WHEN m.tmdb_id IS NOT NULL THEN 0 ELSE 1 END, m.id ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("limit", limit.coerceAtLeast(1))
            .resultList
            .map { (it as Number).toLong() }

    @Transactional
    fun markTmdbResolutionChecked(movieId: Long): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE movies
                SET tmdb_resolution_checked_at = NOW(),
                    updated_at = NOW()
                WHERE id = :movieId
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .executeUpdate()
}
