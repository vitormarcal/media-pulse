package dev.marcal.mediapulse.server.repository.crud

import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class MovieCompanyMembersRepository(
    private val entityManager: EntityManager,
) {
    data class CompanyRecord(
        val id: Long,
        val tmdbId: String,
        val name: String,
        val logoUrl: String?,
        val originCountry: String?,
    )

    data class MemberRecord(
        val tmdbId: String,
        val title: String,
        val originalTitle: String?,
        val year: Int?,
        val overview: String?,
        val posterUrl: String?,
        val localMovieId: Long?,
        val localSlug: String?,
    )

    data class MemberSnapshot(
        val tmdbId: String,
        val title: String,
        val originalTitle: String?,
        val year: Int?,
        val overview: String?,
        val posterUrl: String?,
    )

    fun findCompany(companyId: Long): CompanyRecord? =
        (
            entityManager
                .createNativeQuery(
                    """SELECT id, tmdb_id, name, logo_url, origin_country FROM movie_companies WHERE id = :companyId LIMIT 1""",
                ).setParameter("companyId", companyId)
                .resultList
                .firstOrNull() as Array<*>?
        )?.let { fields ->
            CompanyRecord(
                (fields[0] as Number).toLong(),
                fields[1] as String,
                fields[2] as String,
                fields[3] as String?,
                fields[4] as String?,
            )
        }

    fun findPendingCompanyIds(limit: Int): List<Long> =
        entityManager
            .createNativeQuery(
                """
                SELECT id FROM movie_companies
                WHERE members_synced_at IS NULL
                  AND (members_sync_attempted_at IS NULL OR members_sync_attempted_at <= NOW() - INTERVAL '1 day')
                ORDER BY members_sync_attempted_at NULLS FIRST, id
                LIMIT :limit
                """.trimIndent(),
            ).setParameter("limit", limit)
            .resultList
            .map { (it as Number).toLong() }

    fun findMembers(companyId: Long): List<MemberRecord> =
        entityManager
            .createNativeQuery(
                """
                SELECT member.tmdb_id, member.title, member.original_title, member.release_year,
                       member.overview, member.poster_url, movie.id, movie.slug
                FROM movie_company_members member
                LEFT JOIN movies movie ON movie.tmdb_id = member.tmdb_id
                WHERE member.company_id = :companyId
                ORDER BY member.position, member.id
                """.trimIndent(),
            ).setParameter("companyId", companyId)
            .resultList
            .map { row ->
                val fields = row as Array<*>
                MemberRecord(
                    fields[0] as String,
                    fields[1] as String,
                    fields[2] as String?,
                    (fields[3] as Number?)?.toInt(),
                    fields[4] as String?,
                    fields[5] as String?,
                    (fields[6] as Number?)?.toLong(),
                    fields[7] as String?,
                )
            }

    fun replaceSnapshot(
        companyId: Long,
        members: List<MemberSnapshot>,
    ) {
        entityManager
            .createNativeQuery("DELETE FROM movie_company_members WHERE company_id = :companyId")
            .setParameter("companyId", companyId)
            .executeUpdate()
        members.forEachIndexed { position, member ->
            entityManager
                .createNativeQuery(
                    """
                    INSERT INTO movie_company_members(
                      company_id, tmdb_id, title, original_title, release_year, overview, poster_url, position, updated_at
                    ) VALUES (
                      :companyId, :tmdbId, :title, :originalTitle, :releaseYear, :overview, :posterUrl, :position, NOW()
                    )
                    """.trimIndent(),
                ).setParameter("companyId", companyId)
                .setParameter("tmdbId", member.tmdbId)
                .setParameter("title", member.title)
                .setParameter("originalTitle", member.originalTitle)
                .setParameter("releaseYear", member.year)
                .setParameter("overview", member.overview)
                .setParameter("posterUrl", member.posterUrl)
                .setParameter("position", position)
                .executeUpdate()
        }
        entityManager
            .createNativeQuery(
                """
                UPDATE movie_companies
                SET members_synced_at = NOW(), members_sync_attempted_at = NOW(), members_sync_error = NULL, updated_at = NOW()
                WHERE id = :companyId
                """.trimIndent(),
            ).setParameter("companyId", companyId)
            .executeUpdate()
    }

    fun markSyncFailure(
        companyId: Long,
        error: String,
    ) {
        entityManager
            .createNativeQuery(
                """
                UPDATE movie_companies
                SET members_sync_attempted_at = NOW(), members_sync_error = :error, updated_at = NOW()
                WHERE id = :companyId
                """.trimIndent(),
            ).setParameter("companyId", companyId)
            .setParameter("error", error.take(500))
            .executeUpdate()
    }
}
