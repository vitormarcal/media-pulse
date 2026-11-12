package dev.marcal.mediapulse.server.repository.hardcover

import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverUserBook
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class HardcoverAuthorCandidateRepository(
    private val jdbc: JdbcTemplate,
) {
    // Zero records missing external identity: it must not become a positive match.
    fun observe(item: HardcoverUserBook): Set<Long> {
        val authors = linkedSetOf<Long>()
        for (contribution in item.edition?.contributions.orEmpty()) {
            val name =
                contribution.author
                    ?.name
                    ?.trim()
                    ?.takeIf(String::isNotBlank) ?: continue
            val id =
                jdbc
                    .query(
                        "SELECT id FROM authors WHERE fingerprint = ? FOR UPDATE",
                        { rs, _ -> rs.getLong(1) },
                        FingerprintUtil.authorFp(name),
                    ).firstOrNull()
                    ?: continue
            val externalId = contribution.author?.id?.takeIf { it in 1..Int.MAX_VALUE.toLong() } ?: 0L
            jdbc.update(
                "INSERT INTO hardcover_author_candidates(author_id, hardcover_id) VALUES (?, ?) ON CONFLICT DO NOTHING",
                id,
                externalId,
            )
            authors.add(id)
        }
        return authors
    }

    fun uniqueIdentity(authorId: Long): Long? =
        jdbc
            .query(
                """SELECT c.hardcover_id FROM hardcover_author_candidates c WHERE c.author_id = ? AND c.hardcover_id > 0
        AND NOT EXISTS (SELECT 1 FROM hardcover_author_candidates d WHERE d.author_id = c.author_id AND d.hardcover_id <> c.hardcover_id)
        AND NOT EXISTS (SELECT 1 FROM hardcover_author_candidates d WHERE d.author_id <> c.author_id AND d.hardcover_id = c.hardcover_id)""",
                { rs, _ -> rs.getLong(1) },
                authorId,
            ).singleOrNull()

    fun claim(
        authorId: Long,
        hardcoverId: Long,
    ): Boolean =
        jdbc.update(
            """UPDATE hardcover_author_candidates SET last_attempt_at = CURRENT_TIMESTAMP
        WHERE author_id = ? AND hardcover_id = ? AND (last_attempt_at IS NULL OR last_attempt_at < CURRENT_TIMESTAMP - INTERVAL '1 day')""",
            authorId,
            hardcoverId,
        ) == 1
}
