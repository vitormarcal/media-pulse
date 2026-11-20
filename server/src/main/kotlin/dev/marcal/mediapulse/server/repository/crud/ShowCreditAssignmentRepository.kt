package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.movie.MovieCreditType
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class ShowCreditAssignmentRepository(
    private val entityManager: EntityManager,
) {
    data class UpsertShowCreditRequest(
        val showId: Long,
        val personId: Long,
        val creditType: MovieCreditType,
        val department: String? = null,
        val job: String? = null,
        val characterName: String? = null,
        val billingOrder: Int? = null,
        val manuallySelected: Boolean = false,
    )

    @Transactional
    fun replaceForShow(
        showId: Long,
        credits: List<UpsertShowCreditRequest>,
    ) {
        lockShow(showId)
        entityManager
            .createNativeQuery(
                """
                DELETE FROM show_credits
                WHERE show_id = :showId AND NOT manually_selected
                """.trimIndent(),
            ).setParameter("showId", showId)
            .executeUpdate()

        credits.forEach(::upsert)
    }

    @Transactional
    fun upsert(credit: UpsertShowCreditRequest) {
        lockShow(credit.showId)
        entityManager
            .createNativeQuery(
                """
                INSERT INTO show_credits (
                    show_id,
                    person_id,
                    credit_type,
                    department,
                    job,
                    character_name,
                    billing_order,
                    manually_selected
                ) SELECT
                    :showId,
                    :personId,
                    :creditType,
                    :department,
                    :job,
                    :characterName,
                    :billingOrder,
                    :manuallySelected
                WHERE :manuallySelected OR NOT EXISTS (
                    SELECT 1 FROM show_credits protected
                    WHERE protected.show_id = :showId AND protected.person_id = :personId AND protected.manually_selected
                      AND CASE WHEN protected.credit_type = 'CAST' THEN 'CAST'
                               WHEN protected.job = 'Director' THEN 'DIRECTING' ELSE 'WRITING' END = :category
                )
                ON CONFLICT (show_id, person_id, credit_type, COALESCE(job, ''), COALESCE(character_name, ''))
                DO UPDATE SET
                    department = EXCLUDED.department,
                    billing_order = EXCLUDED.billing_order,
                    manually_selected = show_credits.manually_selected OR EXCLUDED.manually_selected
                """.trimIndent(),
            ).setParameter("showId", credit.showId)
            .setParameter("personId", credit.personId)
            .setParameter("creditType", credit.creditType.name)
            .setParameter("department", credit.department)
            .setParameter("job", credit.job)
            .setParameter("characterName", credit.characterName)
            .setParameter("billingOrder", credit.billingOrder)
            .setParameter("manuallySelected", credit.manuallySelected)
            .setParameter(
                "category",
                when {
                    credit.creditType == MovieCreditType.CAST -> "CAST"
                    credit.job == "Director" -> "DIRECTING"
                    else -> "WRITING"
                },
            ).executeUpdate()
    }

    @Transactional
    fun deleteCategory(
        showId: Long,
        personId: Long,
        category: String,
    ): Int {
        lockShow(showId)
        val predicate =
            when (category) {
                "CAST" -> "credit_type = 'CAST'"
                "DIRECTING" -> "credit_type = 'CREW' AND job = 'Director'"
                "WRITING" -> "credit_type = 'CREW' AND job IN ('Writer', 'Screenplay', 'Story Editor')"
                else -> return 0
            }
        return entityManager
            .createNativeQuery(
                "DELETE FROM show_credits WHERE show_id = :showId AND person_id = :personId AND $predicate",
            ).setParameter("showId", showId)
            .setParameter("personId", personId)
            .executeUpdate()
    }

    private fun lockShow(showId: Long) {
        entityManager
            .createNativeQuery("SELECT id FROM tv_shows WHERE id = :id FOR UPDATE")
            .setParameter("id", showId)
            .singleResult
    }
}
