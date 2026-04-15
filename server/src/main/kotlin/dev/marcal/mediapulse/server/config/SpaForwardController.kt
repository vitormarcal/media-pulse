package dev.marcal.mediapulse.server.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class SpaForwardController {
    @GetMapping(
        "/",
        "/music",
        "/music/library",
        "/music/artists/{id}",
        "/shows",
        "/shows/library",
        "/shows/{slug}",
        "/movies",
        "/movies/library",
        "/movies/{slug}",
        "/books",
        "/books/library",
        "/books/authors/{id}",
        "/books/{slug}",
        "/music/albums/{id}",
        "/music/admin/duplicates",
    )
    fun forwardToIndex(): String = "forward:/index.html"
}
