package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.service.movie.MoviePersonalMarksService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/movies")
class MoviePersonalMarksController(
    private val service: MoviePersonalMarksService,
) {
    @PostMapping("/{movieId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markFavorite(
        @PathVariable movieId: Long,
    ) = service.setFavorite(movieId, true)

    @DeleteMapping("/{movieId}/favorite")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unmarkFavorite(
        @PathVariable movieId: Long,
    ) = service.setFavorite(movieId, false)

    @PostMapping("/{movieId}/abandoned")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun markAbandoned(
        @PathVariable movieId: Long,
    ) = service.setAbandoned(movieId, true)

    @DeleteMapping("/{movieId}/abandoned")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun unmarkAbandoned(
        @PathVariable movieId: Long,
    ) = service.setAbandoned(movieId, false)
}
