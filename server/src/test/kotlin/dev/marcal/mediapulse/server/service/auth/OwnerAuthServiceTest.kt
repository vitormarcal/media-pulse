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
    fun `reset attempts are limited by client and do not exhaust login attempts`() {
        val repository = mockk<OwnerAuthRepository>()
        every { repository.credential() } returns OwnerAuthRepository.Credential(null, 0)
        val service = OwnerAuthService(repository, OwnerAuthProperties())
        repeat(10) {
            assertEquals(
                HttpStatus.UNAUTHORIZED,
                assertFailsWith<ResponseStatusException> { service.reset("wrong", "new-password", "client-a") }.statusCode,
            )
        }
        assertEquals(
            HttpStatus.TOO_MANY_REQUESTS,
            assertFailsWith<ResponseStatusException> { service.reset("wrong", "new-password", "client-a") }.statusCode,
        )
        assertEquals(
            HttpStatus.UNAUTHORIZED,
            assertFailsWith<ResponseStatusException> { service.reset("wrong", "new-password", "client-b") }.statusCode,
        )
        assertEquals(
            HttpStatus.UNAUTHORIZED,
            assertFailsWith<ResponseStatusException> { service.login("wrong", "client-a") }.statusCode,
        )
    }

    @Test
    fun `malformed reset authorization prevents startup`() {
        assertFailsWith<IllegalArgumentException> { OwnerAuthProperties("not-a-uuid") }
    }
}
