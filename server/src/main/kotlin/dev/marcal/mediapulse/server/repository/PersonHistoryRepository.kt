package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.people.PersonHistoryCategory
import dev.marcal.mediapulse.server.api.people.PersonHistoryItemDto
import dev.marcal.mediapulse.server.api.people.PersonHistoryPageResponse
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Repository

@Repository
class PersonHistoryRepository(
    private val entityManager: EntityManager,
) {
    fun findMostPresent(
        category: PersonHistoryCategory,
        limit: Int,
        offset: Int,
    ): PersonHistoryPageResponse {
        val movieCreditFilter = category.movieCreditFilter("mc")
        val showCreditFilter = category.showCreditFilter("sc")
        val rows =
            entityManager
                .createNativeQuery(
                    """
                    WITH watched_works AS (
                      SELECT
                        mc.person_id,
                        'MOVIE' AS work_type,
                        mc.movie_id AS work_id,
                        MAX(mw.watched_at) AS last_watched_at
                      FROM movie_credits mc
                      JOIN movie_watches mw ON mw.movie_id = mc.movie_id
                      WHERE $movieCreditFilter
                      GROUP BY mc.person_id, mc.movie_id

                      UNION ALL

                      SELECT
                        sc.person_id,
                        'SHOW' AS work_type,
                        sc.show_id AS work_id,
                        MAX(tew.watched_at) AS last_watched_at
                      FROM show_credits sc
                      JOIN tv_episodes te ON te.show_id = sc.show_id
                      JOIN tv_episode_watches tew ON tew.episode_id = te.id
                      WHERE $showCreditFilter
                      GROUP BY sc.person_id, sc.show_id
                    )
                    SELECT
                      p.id,
                      p.name,
                      p.slug,
                      p.profile_url,
                      COUNT(*) AS watched_works_count,
                      MAX(ww.last_watched_at) AS latest_watch
                    FROM watched_works ww
                    JOIN people p ON p.id = ww.person_id
                    WHERE p.favorited_at IS NULL
                    GROUP BY p.id, p.name, p.slug, p.profile_url
                    HAVING COUNT(*) >= 2
                    ORDER BY watched_works_count DESC, latest_watch DESC, p.name ASC, p.id ASC
                    LIMIT :limitPlusOne
                    OFFSET :offset
                    """.trimIndent(),
                ).setParameter("limitPlusOne", limit + 1)
                .setParameter("offset", offset)
                .resultList

        val items =
            rows.take(limit).map { row ->
                val fields = row as Array<*>
                PersonHistoryItemDto(
                    personId = (fields[0] as Number).toLong(),
                    name = fields[1] as String,
                    slug = fields[2] as String,
                    profileUrl = fields[3] as String?,
                    watchedWorksCount = (fields[4] as Number).toLong(),
                )
            }

        return PersonHistoryPageResponse(
            items = items,
            nextOffset = if (rows.size > limit) offset + items.size else null,
        )
    }

    private fun PersonHistoryCategory.movieCreditFilter(alias: String): String =
        when (this) {
            PersonHistoryCategory.CAST -> "$alias.credit_type = 'CAST'"
            PersonHistoryCategory.DIRECTING -> "$alias.job = 'Director'"
            PersonHistoryCategory.WRITING -> "$alias.job IN ('Writer', 'Screenplay', 'Story')"
        }

    private fun PersonHistoryCategory.showCreditFilter(alias: String): String =
        when (this) {
            PersonHistoryCategory.CAST -> "$alias.credit_type = 'CAST'"
            PersonHistoryCategory.DIRECTING -> "$alias.job = 'Director'"
            PersonHistoryCategory.WRITING -> "$alias.job IN ('Writer', 'Screenplay', 'Story Editor')"
        }
}
