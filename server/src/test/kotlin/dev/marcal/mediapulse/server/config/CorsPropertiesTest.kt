package dev.marcal.mediapulse.server.config

import org.junit.jupiter.api.Test
import org.springframework.boot.context.properties.bind.Bindable
import org.springframework.boot.context.properties.bind.Binder
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class CorsPropertiesTest {
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
