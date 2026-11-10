package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.MovieImageSelectionRequest
import dev.marcal.mediapulse.server.service.movie.MovieImageSelectionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/movies/{movieId}/images")
class MovieImageSelectionController(
    private val service: MovieImageSelectionService,
) {
    @GetMapping("/candidates")
    fun candidates(
        @PathVariable movieId: Long,
    ) = service.candidates(movieId)

    @PostMapping("/lookup")
    fun lookup(
        @PathVariable movieId: Long,
        @RequestBody request: MovieImageSelectionRequest,
    ) = service.lookup(movieId, request)

    @PostMapping("/selection")
    fun save(
        @PathVariable movieId: Long,
        @RequestBody request: MovieImageSelectionRequest,
    ) = service.save(movieId, request)
}
