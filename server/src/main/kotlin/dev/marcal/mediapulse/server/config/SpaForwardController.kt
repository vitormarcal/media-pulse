package dev.marcal.mediapulse.server.config

import org.springframework.stereotype.Controller
import org.springframework.web.bind.annotation.GetMapping

@Controller
class SpaForwardController {
    @GetMapping(
        "/",
        "/music",
        "/music/artists/{id}",
        "/shows",
        "/shows/{slug}",
        "/shows/{slug}/seasons/{seasonNumber}",
        "/movies",
        "/movies/lists",
        "/movies/collections",
        "/movies/collections/{id}",
        "/movies/{slug}",
        "/movies/lists/{slug}",
        "/people/{slug}",
        "/movies/companies/{slug}",
        "/movies/terms/{kind}/{slug}",
        "/books",
        "/books/authors/{id}",
        "/books/{slug}",
        "/games",
        "/games/",
        "/games/{slug}",
        "/games/{slug}/",
        "/music/albums/{id}",
        "/music/lists",
        "/music/lists/{slug}",
        "/music/terms/{kind}/{slug}",
        "/music/admin/duplicates",
    )
    fun forwardToIndex(): String = "forward:/index.html"
}
