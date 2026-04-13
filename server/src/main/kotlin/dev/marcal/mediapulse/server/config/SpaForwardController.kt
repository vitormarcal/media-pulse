package dev.marcal.mediapulse.server.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class SpaForwardController {
    @GetMapping(
        "/",
        "/shows",
        "/shows/library",
        "/shows/{slug}",
        "/movies",
        "/movies/library",
        "/movies/{slug}",
        "/books/{slug}",
        "/music/albums/{id}",
    )
    fun forwardToIndex(): String = "forward:/index.html"
}
