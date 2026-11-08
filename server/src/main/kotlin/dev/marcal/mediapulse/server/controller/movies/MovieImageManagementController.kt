package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.MovieImageReferenceRequest
import dev.marcal.mediapulse.server.service.movie.MovieImageManagementService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/movies/{movieId}/images")
class MovieImageManagementController(
    private val service: MovieImageManagementService,
) {
    @PostMapping("/primary")
    fun primary(
        @PathVariable movieId: Long,
        @RequestBody request: MovieImageReferenceRequest,
    ) = service.update(movieId, request.url, false)

    @DeleteMapping
    fun remove(
        @PathVariable movieId: Long,
        @RequestBody request: MovieImageReferenceRequest,
    ) = service.update(movieId, request.url, true)
}
