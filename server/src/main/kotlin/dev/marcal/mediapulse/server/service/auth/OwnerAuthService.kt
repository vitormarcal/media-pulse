package dev.marcal.mediapulse.server.service.auth

import dev.marcal.mediapulse.server.config.OwnerAuthProperties
import dev.marcal.mediapulse.server.repository.OwnerAuthRepository
import org.springframework.http.HttpStatus
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.security.MessageDigest

@Service
class OwnerAuthService(
    private val repository: OwnerAuthRepository,
    private val props: OwnerAuthProperties,
) {
    private val encoder = BCryptPasswordEncoder(12)
    private val dummyHash = encoder.encode("unavailable-owner-password")
    private var windowStart = 0L
    private var attempts = 0

    @Synchronized
    fun checkRate() {
        val now = System.currentTimeMillis()
        if (now - windowStart >= 60_000) {
            windowStart = now
            attempts = 0
        }
        if (++attempts > 10) throw ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Aguarde um minuto antes de tentar novamente")
    }

    fun generation(): Long = repository.credential().generation

    fun login(password: String): Long {
        checkRate()
        val credential = repository.credential()
        if (password.toByteArray().size > 72 ||
            !encoder.matches(password, credential.passwordHash ?: dummyHash) ||
            credential.passwordHash == null
        ) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Senha inválida")
        }
        return credential.generation
    }

    @Transactional
    fun reset(
        token: String,
        password: String,
    ) {
        checkRate()
        if (props.resetToken.isBlank() || !equal(props.resetToken.lowercase(), token.lowercase())) {
            throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autorização inválida ou consumida")
        }
        if (password.length < 12 || password.toByteArray().size > 72) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Use pelo menos 12 caracteres e no máximo 72 bytes")
        }
        val hash = encoder.encode(password)
        repository.credential(lock = true)
        val digest = MessageDigest.getInstance("SHA-256").digest(token.lowercase().toByteArray()).joinToString("") { "%02x".format(it) }
        if (!repository.consume(digest)) throw ResponseStatusException(HttpStatus.UNAUTHORIZED, "Autorização inválida ou consumida")
        repository.changePassword(hash)
    }

    companion object {
        fun equal(
            expected: String,
            actual: String,
        ): Boolean = MessageDigest.isEqual(expected.toByteArray(), actual.toByteArray())
    }
}
