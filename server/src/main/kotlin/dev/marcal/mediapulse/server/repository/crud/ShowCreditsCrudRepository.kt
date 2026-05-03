package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class ShowCreditsCrudRepository(
    private val entityManager: EntityManager,
) {
    data class ShowCreditsSyncCandidate(
        val showId: Long,
        val tmdbId: String,
    )

    fun findTmdbSyncCandidates(limit: Int): List<ShowCreditsSyncCandidate> =
        entityManager
            .createNativeQuery(
                """
                SELECT s.id, ei.external_id
                FROM tv_shows s
                JOIN external_identifiers ei
                  ON ei.entity_type = 'SHOW'
                 AND ei.entity_id = s.id
                 AND ei.provider = 'TMDB'
                WHERE s.credits_synced_at IS NULL
                ORDER BY s.id ASC
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("limit", limit.coerceAtLeast(1))
            .resultList
            .map { row ->
                val fields = row as Array<*>
                ShowCreditsSyncCandidate(
                    showId = (fields[0] as Number).toLong(),
                    tmdbId = fields[1] as String,
                )
            }

    fun countPendingTmdbSyncCandidates(): Int =
        (
            entityManager
                .createNativeQuery(
                    """
                    SELECT COUNT(*)
                    FROM tv_shows s
                    JOIN external_identifiers ei
                      ON ei.entity_type = 'SHOW'
                     AND ei.entity_id = s.id
                     AND ei.provider = 'TMDB'
                    WHERE s.credits_synced_at IS NULL
                    """.trimIndent(),
                ).singleResult as Number
        ).toInt()

    fun markCreditsSynced(showId: Long): Int =
        entityManager
            .createNativeQuery(
                """
                UPDATE tv_shows
                SET credits_synced_at = NOW(),
                    updated_at = NOW()
                WHERE id = :showId
                """.trimIndent(),
            ).setParameter("showId", showId)
            .executeUpdate()

    data class LocalShowByTmdbId(
        val tmdbId: String,
        val showId: Long,
        val slug: String?,
    )

    fun findLocalShowsByTmdbIds(tmdbIds: List<String>): Map<String, LocalShowByTmdbId> {
        val normalizedIds = tmdbIds.mapNotNull { it.trim().ifBlank { null } }.distinct()
        if (normalizedIds.isEmpty()) return emptyMap()

        return entityManager
            .createNativeQuery(
                """
                SELECT ei.external_id, s.id, s.slug
                FROM external_identifiers ei
                JOIN tv_shows s ON s.id = ei.entity_id
                WHERE ei.entity_type = 'SHOW'
                  AND ei.provider = 'TMDB'
                  AND ei.external_id IN (:tmdbIds)
                """.trimIndent(),
            ).setParameter("tmdbIds", normalizedIds)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                LocalShowByTmdbId(
                    tmdbId = fields[0] as String,
                    showId = (fields[1] as Number).toLong(),
                    slug = fields[2] as String?,
                )
            }.associateBy { it.tmdbId }
    }
}
