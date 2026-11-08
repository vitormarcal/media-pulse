package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.service.movie.MovieCollectionTagSyncService
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/movies/{movieId}/collection-tags/sync")
class MovieCollectionTagSyncController(
    private val service: MovieCollectionTagSyncService,
) {
    @GetMapping
    fun state(
        @PathVariable movieId: Long,
    ) = service.state(movieId)

    @PostMapping
    fun retry(
        @PathVariable movieId: Long,
    ) = service.retry(movieId)

    @ExceptionHandler(ResponseStatusException::class)
    fun invalid(error: ResponseStatusException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(error.statusCode)
            .body(ProblemDetail.forStatusAndDetail(error.statusCode, error.reason ?: "Não foi possível atualizar as tags das imagens."))
}
