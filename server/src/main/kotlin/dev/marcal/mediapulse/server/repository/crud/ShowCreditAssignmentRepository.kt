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
    )

    @Transactional
    fun replaceForShow(
        showId: Long,
        credits: List<UpsertShowCreditRequest>,
    ) {
        entityManager
            .createNativeQuery(
                """
                DELETE FROM show_credits
                WHERE show_id = :showId
                """.trimIndent(),
            ).setParameter("showId", showId)
            .executeUpdate()

        credits.forEach(::upsert)
    }

    @Transactional
    fun upsert(credit: UpsertShowCreditRequest) {
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
                    billing_order
                ) VALUES (
                    :showId,
                    :personId,
                    :creditType,
                    :department,
                    :job,
                    :characterName,
                    :billingOrder
                )
                ON CONFLICT (show_id, person_id, credit_type, COALESCE(job, ''), COALESCE(character_name, ''))
                DO UPDATE SET
                    department = EXCLUDED.department,
                    billing_order = EXCLUDED.billing_order
                """.trimIndent(),
            ).setParameter("showId", credit.showId)
            .setParameter("personId", credit.personId)
            .setParameter("creditType", credit.creditType.name)
            .setParameter("department", credit.department)
            .setParameter("job", credit.job)
            .setParameter("characterName", credit.characterName)
            .setParameter("billingOrder", credit.billingOrder)
            .executeUpdate()
    }
}
