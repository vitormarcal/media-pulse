package dev.marcal.mediapulse.server.repository.query

import dev.marcal.mediapulse.server.model.magazine.MagazineReadStatus
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class MagazineQueryRepository(
    private val jdbc: NamedParameterJdbcTemplate,
) {
    fun issueIds(
        q: String?,
        publicationId: Long?,
        status: MagazineReadStatus?,
        page: Int,
        limit: Int,
    ): List<Long> =
        jdbc.query(
            """
            SELECT i.id FROM magazine_issues i
            JOIN magazine_publications p ON p.id = i.publication_id
            LEFT JOIN LATERAL (SELECT status FROM magazine_reads WHERE issue_id = i.id ORDER BY (status IN ('WANT_TO_READ', 'CURRENTLY_READING')) DESC, id DESC LIMIT 1) r ON true
            WHERE (CAST(:publicationId AS BIGINT) IS NULL OR p.id = :publicationId)
              AND (CAST(:status AS TEXT) IS NULL OR r.status = :status)
              AND (CAST(:q AS TEXT) IS NULL OR strpos(lower(concat_ws(' ', p.name, i.number, i.cover_date, split_part(i.cover_date, '-', 2) || '/' || split_part(i.cover_date, '-', 1), p.issn)), :q) > 0)
            ORDER BY i.activity_date DESC, i.id DESC LIMIT :fetchLimit OFFSET :offset
            """.trimIndent(),
            mapOf(
                "publicationId" to publicationId,
                "status" to status?.name,
                "q" to q?.trim()?.lowercase()?.ifBlank { null },
                "fetchLimit" to limit + 1,
                "offset" to page.toLong() * limit,
            ),
        ) { rs, _ -> rs.getLong("id") }
}
