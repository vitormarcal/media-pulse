package dev.marcal.mediapulse.server.config

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CorsPropertiesTest {
    @Test
    fun `forwarded HTTPS is applied before CORS for same origin reset`() {
        val cors = CorsConfig(CorsProperties()).corsFilter()
        val request =
            org.springframework.mock.web.MockHttpServletRequest("POST", "/api/auth/reset").apply {
                serverName = "media.example.test"
                serverPort = 80
                addHeader("Origin", "https://media.example.test")
                addHeader("X-Forwarded-Proto", "https")
                addHeader("X-Forwarded-Host", "media.example.test")
                addHeader("X-Forwarded-Port", "443")
            }
        val response =
            org.springframework.mock.web
                .MockHttpServletResponse()
        val filters =
            listOf(
                cors.order to cors.filter,
                org.springframework.core.Ordered.HIGHEST_PRECEDENCE to
                    org.springframework.web.filter
                        .ForwardedHeaderFilter(),
            ).sortedBy { it.first }.map { it.second }
        val endpoint =
            object : jakarta.servlet.http.HttpServlet() {
                override fun service(
                    req: jakarta.servlet.http.HttpServletRequest,
                    res: jakarta.servlet.http.HttpServletResponse,
                ) {
                    assertEquals(true, req.isSecure)
                    res.status = 204
                }
            }
        org.springframework.mock.web
            .MockFilterChain(endpoint, *filters.toTypedArray<jakarta.servlet.Filter>())
            .doFilter(request, response)
        assertEquals(204, response.status)
    }

    @Test
    fun `indexed origins bind as an explicit list for credentialed requests`() {
        val source =
            MapConfigurationPropertySource(
                mapOf(
                    "media-pulse.allowed-origin[0]" to "http://localhost:3000",
                    "media-pulse.allowed-origin[1]" to "http://localhost:8080",
                ),
            )
        val props = Binder(source).bind("media-pulse", Bindable.of(CorsProperties::class.java)).get()
        assertEquals(listOf("http://localhost:3000", "http://localhost:8080"), props.allowedOrigin)
        assertFailsWith<IllegalArgumentException> { CorsProperties(listOf("*")) }
    }

    @Test
    fun `CORS headers precede authorization errors and reject unknown origins`() {
        val registration = CorsConfig(CorsProperties(listOf("http://localhost:3000"))).corsFilter()
        val request =
            org.springframework.mock.web.MockHttpServletRequest("POST", "/api/books/series").apply {
                addHeader("Origin", "http://localhost:3000")
            }
        val response =
            org.springframework.mock.web
                .MockHttpServletResponse()
        registration.filter.doFilter(request, response) { _, reply ->
            (reply as jakarta.servlet.http.HttpServletResponse).sendError(401)
        }
        assertEquals(401, response.status)
        assertEquals("http://localhost:3000", response.getHeader("Access-Control-Allow-Origin"))
        assertEquals("true", response.getHeader("Access-Control-Allow-Credentials"))
        val rejected =
            org.springframework.mock.web
                .MockHttpServletResponse()
        request.removeHeader("Origin")
        request.addHeader("Origin", "https://unknown.invalid")
        registration.filter.doFilter(request, rejected) { _, _ -> error("Unknown origin must be rejected") }
        assertEquals(403, rejected.status)
    }
}
