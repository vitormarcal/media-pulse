package dev.marcal.mediapulse.server.config

import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.GetMapping
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SpaForwardControllerTest {
    @Test
    fun `magazine publication routes support direct navigation and refresh`() {
        val routes =
            SpaForwardController::class.java
                .getDeclaredMethod(
                    "forwardToIndex",
                ).getAnnotation(GetMapping::class.java)
                .value
                .toList()
        assertContains(routes, "/magazines/publications")
        assertContains(routes, "/magazines/publications/{id}")
        assertContains(routes, "/magazines/publications/{id}/")
    }

    @Test
    fun `artwork library artist and list routes support direct navigation`() {
        val routes =
            SpaForwardController::class.java
                .getDeclaredMethod("forwardToIndex")
                .getAnnotation(GetMapping::class.java)
                .value
                .toList()
        for (route in listOf(
            "/artworks",
            "/artworks/{id}",
            "/artworks/artists",
            "/artworks/artists/{id}",
            "/artworks/lists",
            "/artworks/lists/{id}/",
        )) {
            assertContains(routes, route)
        }
    }

    @Test
    fun `people index should be forwarded to the spa`() {
        val method = SpaForwardController::class.java.getDeclaredMethod("forwardToIndex")
        val routes = method.getAnnotation(GetMapping::class.java).value.toList()

        assertContains(routes, "/people")
        assertEquals("forward:/index.html", SpaForwardController().forwardToIndex())
    }

    @Test
    fun `show list pages should be forwarded to the spa`() {
        val method = SpaForwardController::class.java.getDeclaredMethod("forwardToIndex")
        val routes = method.getAnnotation(GetMapping::class.java).value.toList()

        assertContains(routes, "/shows/lists")
        assertContains(routes, "/shows/lists/{slug}")
    }
}
