package dev.marcal.mediapulse.server.repository.hardcover

import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.apache.commons.codec.digest.DigestUtils
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.transaction.annotation.Transactional

@Repository
class HardcoverAuthorIdentityRepository(
    private val jdbc: JdbcTemplate,
) {
    @Transactional
    fun resolve(
        name: String,
        hardcoverId: Long,
    ): Long {
        require(hardcoverId in 1..Int.MAX_VALUE.toLong())
        // Serialize competing imports of the same external identity, including renamed profiles.
        jdbc.query("SELECT pg_advisory_xact_lock(82421, ?)", { _, _ -> true }, hardcoverId.toInt())
        bound(hardcoverId)?.let { return it }
        val confirmed =
            jdbc
                .query(
                    """SELECT a.id FROM authors a JOIN book_author_profiles p ON p.author_id = a.id
            WHERE p.hardcover_id = ? AND p.managed_manually AND a.hardcover_import_id IS NULL
            AND NOT EXISTS (SELECT 1 FROM hardcover_author_candidates c WHERE c.author_id = a.id AND c.hardcover_id <> p.hardcover_id)
            FOR UPDATE OF a""",
                    { rs, _ -> rs.getLong(1) },
                    hardcoverId,
                ).firstOrNull()
        if (confirmed != null) {
            val bound =
                jdbc
                    .query(
                        """UPDATE authors a SET hardcover_import_id = ? WHERE a.id = ? AND a.hardcover_import_id IS NULL
                AND EXISTS (SELECT 1 FROM book_author_profiles p WHERE p.author_id = a.id AND p.hardcover_id = ? AND p.managed_manually)
                AND NOT EXISTS (SELECT 1 FROM hardcover_author_candidates c WHERE c.author_id = a.id AND c.hardcover_id <> ?)
                RETURNING a.id""",
                        { rs, _ -> rs.getLong(1) },
                        hardcoverId,
                        confirmed,
                        hardcoverId,
                        hardcoverId,
                    ).firstOrNull()
            if (bound != null) return bound
            bound(hardcoverId)?.let { return it }
        }
        // The familiar name fingerprint is kept when free; homonyms get a provider identity fingerprint.
        val fingerprints = listOf(FingerprintUtil.authorFp(name), DigestUtils.sha256Hex("hardcover-author|$hardcoverId"))
        for (fingerprint in fingerprints) {
            val created =
                jdbc
                    .query(
                        """INSERT INTO authors(name, fingerprint, hardcover_import_id) VALUES (?, ?, ?)
                ON CONFLICT DO NOTHING RETURNING id""",
                        { rs, _ -> rs.getLong(1) },
                        name,
                        fingerprint,
                        hardcoverId,
                    ).firstOrNull()
            if (created != null) return created
            bound(hardcoverId)?.let { return it }
        }
        error("Unable to resolve Hardcover author identity")
    }

    fun bound(hardcoverId: Long): Long? =
        jdbc.query("SELECT id FROM authors WHERE hardcover_import_id = ?", { rs, _ -> rs.getLong(1) }, hardcoverId).firstOrNull()
}
