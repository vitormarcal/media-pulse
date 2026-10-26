package dev.marcal.mediapulse.server.repository

import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class OwnerAuthRepository(
    private val jdbc: JdbcTemplate,
) {
    data class Credential(
        val passwordHash: String?,
        val generation: Long,
    )

    fun credential(lock: Boolean = false): Credential =
        jdbc.queryForObject(
            "SELECT password_hash, generation FROM owner_credentials WHERE id = 1" + if (lock) " FOR UPDATE" else "",
            { rs, _ -> Credential(rs.getString("password_hash"), rs.getLong("generation")) },
        )!!

    fun consume(tokenHash: String): Boolean =
        jdbc.update("INSERT INTO owner_reset_tokens (token_hash) VALUES (?) ON CONFLICT DO NOTHING", tokenHash) == 1

    fun changePassword(hash: String) {
        jdbc.update("UPDATE owner_credentials SET password_hash = ?, generation = generation + 1 WHERE id = 1", hash)
    }
}
