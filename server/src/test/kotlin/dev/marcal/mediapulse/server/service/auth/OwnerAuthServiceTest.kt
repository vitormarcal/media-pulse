package dev.marcal.mediapulse.server.service.auth

import dev.marcal.mediapulse.server.config.OwnerAuthProperties
import dev.marcal.mediapulse.server.repository.OwnerAuthRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class OwnerAuthServiceTest {
    @Test
    fun `login and reset share a limit even before first password exists`() {
        val repository = mockk<OwnerAuthRepository>()
        every { repository.credential() } returns OwnerAuthRepository.Credential(null, 0)
        val service = OwnerAuthService(repository, OwnerAuthProperties())
        repeat(5) {
            assertEquals(HttpStatus.UNAUTHORIZED, assertFailsWith<ResponseStatusException> { service.login("wrong") }.statusCode)
            assertEquals(
                HttpStatus.UNAUTHORIZED,
                assertFailsWith<ResponseStatusException> { service.reset("wrong", "new-password") }.statusCode,
            )
        }
        assertEquals(HttpStatus.TOO_MANY_REQUESTS, assertFailsWith<ResponseStatusException> { service.login("wrong") }.statusCode)
    }

    @Test
    fun `malformed reset authorization prevents startup`() {
        assertFailsWith<IllegalArgumentException> { OwnerAuthProperties("not-a-uuid") }
    }
}
