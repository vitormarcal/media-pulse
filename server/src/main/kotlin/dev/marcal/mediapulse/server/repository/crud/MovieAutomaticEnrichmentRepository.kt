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
                WHERE (m.tmdb_id IS NULL AND m.imdb_id IS NOT NULL
                      AND m.tmdb_resolution_state IS DISTINCT FROM 'NOT_FOUND' AND (
                        m.tmdb_resolution_checked_at IS NULL
                        OR m.tmdb_resolution_checked_at < NOW() - INTERVAL '1 day'
                      ))
                   OR (m.tmdb_id IS NOT NULL AND (
                        (m.terms_synced_at IS NULL AND (
                          m.terms_sync_attempted_at IS NULL OR m.terms_sync_attempted_at <= NOW() - INTERVAL '1 day'
                        ))
                        OR (m.credits_synced_at IS NULL AND (
                          m.credits_sync_attempted_at IS NULL OR m.credits_sync_attempted_at <= NOW() - INTERVAL '1 day'
                        ))
                        OR (m.companies_synced_at IS NULL AND (
                          m.companies_sync_attempted_at IS NULL OR m.companies_sync_attempted_at <= NOW() - INTERVAL '1 day'
                        ))
                   ))
                ORDER BY CASE WHEN m.tmdb_id IS NOT NULL THEN 0 ELSE 1 END, m.id ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("limit", limit.coerceAtLeast(1))
            .resultList
            .map { (it as Number).toLong() }

    @Transactional
    fun markTmdbResolutionNotFound(movieId: Long): Int = markTmdbResolution(movieId, "NOT_FOUND", null)

    @Transactional
    fun markTmdbResolutionFailure(
        movieId: Long,
        error: String,
    ): Int = markTmdbResolution(movieId, "FAILED", error)

    @Transactional
    fun clearTmdbResolutionResult(movieId: Long): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE movies
                SET tmdb_resolution_checked_at = NOW(),
                    tmdb_resolution_state = NULL,
                    tmdb_resolution_error = NULL,
                    updated_at = NOW()
                WHERE id = :movieId
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .executeUpdate()

    private fun markTmdbResolution(
        movieId: Long,
        state: String,
        error: String?,
    ): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE movies
                SET tmdb_resolution_checked_at = NOW(),
                    tmdb_resolution_state = :state,
                    tmdb_resolution_error = :error,
                    updated_at = NOW()
                WHERE id = :movieId
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .setParameter("state", state)
            .setParameter("error", error?.take(500))
            .executeUpdate()

    @Transactional
    fun markTermsFailure(
        movieId: Long,
        error: String,
    ): Int = markFailure(movieId, "terms", error)

    @Transactional
    fun markCreditsFailure(
        movieId: Long,
        error: String,
    ): Int = markFailure(movieId, "credits", error)

    @Transactional
    fun markCompaniesFailure(
        movieId: Long,
        error: String,
    ): Int = markFailure(movieId, "companies", error)

    private fun markFailure(
        movieId: Long,
        step: String,
        error: String,
    ): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE movies
                SET ${step}_sync_attempted_at = NOW(),
                    ${step}_sync_error = :error,
                    updated_at = NOW()
                WHERE id = :movieId
                """.trimIndent(),
            ).setParameter("movieId", movieId)
            .setParameter("error", error.take(500))
            .executeUpdate()
}
