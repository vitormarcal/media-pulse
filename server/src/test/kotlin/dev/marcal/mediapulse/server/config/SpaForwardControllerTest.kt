package dev.marcal.mediapulse.server.config

import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.GetMapping
import kotlin.test.assertContains
import kotlin.test.assertEquals

class SpaForwardControllerTest {
    @Test
    fun `people index should be forwarded to the spa`() {
        val method = SpaForwardController::class.java.getDeclaredMethod("forwardToIndex")
        val routes = method.getAnnotation(GetMapping::class.java).value.toList()

        assertContains(routes, "/people")
        assertEquals("forward:/index.html", SpaForwardController().forwardToIndex())
    }
}
