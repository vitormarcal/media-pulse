package dev.marcal.mediapulse.server.service.books

import dev.marcal.mediapulse.server.config.OwnerAccessFilter
import dev.marcal.mediapulse.server.config.OwnerAuthProperties
import dev.marcal.mediapulse.server.controller.books.BookAuthorEnrichmentController
import dev.marcal.mediapulse.server.service.auth.OwnerAuthService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.mock.web.MockHttpServletRequest
import org.springframework.mock.web.MockHttpServletResponse
import org.springframework.mock.web.MockHttpSession
import org.springframework.web.bind.annotation.PostMapping
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class BookAuthorEnrichmentAccessTest {
    @Test fun `preview and apply require owner and csrf and are post only`() {
        val auth = mockk<OwnerAuthService>()
        every { auth.generation() } returns 1L
        val filter = OwnerAccessFilter(auth, OwnerAuthProperties())
        for (suffix in listOf("/preview", "")) {
            val path = "/api/books/authors/28/enrichment$suffix"

            fun request(
                owner: Boolean,
                csrf: Boolean,
            ): Int {
                val req =
                    MockHttpServletRequest("POST", path).apply {
                        servletPath = path
                        if (owner) {
                            setSession(
                                MockHttpSession().apply {
                                    setAttribute("ownerGeneration", 1L)
                                    setAttribute("csrf", "token")
                                },
                            )
                        }
                        if (csrf) addHeader("X-CSRF-Token", "token")
                    }
                val response = MockHttpServletResponse()
                filter.doFilter(req, response) { _, _ -> response.status = 204 }
                return response.status
            }
            assertEquals(401, request(false, false))
            assertEquals(403, request(true, false))
            assertEquals(204, request(true, true))
        }
        for (name in listOf("preview", "apply")) {
            assertNotNull(
                BookAuthorEnrichmentController::class.java.declaredMethods
                    .first {
                        it.name ==
                            name
                    }.getAnnotation(PostMapping::class.java),
            )
        }
    }
}
