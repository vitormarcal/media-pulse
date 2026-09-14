package dev.marcal.mediapulse.server.repository

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class PersonCleanupRepository(
    private val entityManager: EntityManager,
) {
    fun deleteIfOrphanAndNotFavorite(personId: Long): Int =
        entityManager
            .createNativeQuery(
                """
                DELETE FROM people p
                WHERE p.id = :personId
                  AND p.favorited_at IS NULL
                  AND NOT EXISTS (SELECT 1 FROM movie_credits mc WHERE mc.person_id = p.id)
                  AND NOT EXISTS (SELECT 1 FROM show_credits sc WHERE sc.person_id = p.id)
                """.trimIndent(),
            ).setParameter("personId", personId)
            .executeUpdate()
}
