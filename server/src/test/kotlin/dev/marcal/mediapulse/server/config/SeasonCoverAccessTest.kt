package dev.marcal.mediapulse.server.config

import dev.marcal.mediapulse.server.service.auth.OwnerAuthService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import kotlin.test.assertEquals

class SeasonCoverAccessTest {
    @Test
    fun `season candidate reads and mutations require owner and csrf as appropriate`() {
        val auth = mockk<OwnerAuthService>()
        every { auth.generation() } returns 1L
        val filter = OwnerAccessFilter(auth, OwnerAuthProperties())
        val owner =
            MockHttpSession().apply {
                setAttribute("ownerGeneration", 1L)
                setAttribute("csrf", "test-token")
            }

        fun status(
            path: String,
            method: String,
            session: MockHttpSession? = null,
            csrf: String? = null,
        ): Int {
            val req =
                MockHttpServletRequest(method, path).apply {
                    servletPath = path
                    session?.let { setSession(it) }
                    csrf?.let { addHeader("X-CSRF-Token", it) }
                }
            val response = MockHttpServletResponse()
            filter.doFilter(req, response) { _, _ -> response.status = 204 }
            return response.status
        }
        for (path in listOf(
            "/api/shows/7/seasons/0/cover/candidates",
            "/api/shows/7/seasons/-1/cover/candidates",
            "/api/shows/7/seasons/1/cover/candidates/",
        )) {
            assertEquals(401, status(path, "GET"))
            assertEquals(401, status(path, "HEAD"))
            assertEquals(204, status(path, "GET", owner))
        }
        for (method in listOf("POST", "DELETE")) {
            assertEquals(401, status("/api/shows/7/seasons/1/cover", method))
            assertEquals(403, status("/api/shows/7/seasons/1/cover", method, owner))
            assertEquals(403, status("/api/shows/7/seasons/1/cover", method, owner, "wrong"))
            assertEquals(204, status("/api/shows/7/seasons/1/cover", method, owner, "test-token"))
        }
        assertEquals(204, status("/api/shows/7/seasons/1", "GET"))
    }
}
