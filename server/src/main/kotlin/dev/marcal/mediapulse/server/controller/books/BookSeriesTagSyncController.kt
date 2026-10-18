package dev.marcal.mediapulse.server.controller.books

import dev.marcal.mediapulse.server.service.books.BookSeriesTagSyncService
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
@RequestMapping("/api/books/{bookId}/series-tags/sync")
class BookSeriesTagSyncController(
    private val service: BookSeriesTagSyncService,
) {
    @GetMapping
    fun state(
        @PathVariable bookId: Long,
    ) = service.state(bookId)

    @PostMapping
    fun retry(
        @PathVariable bookId: Long,
    ) = service.retry(bookId)

    @ExceptionHandler(ResponseStatusException::class)
    fun invalid(error: ResponseStatusException): ResponseEntity<ProblemDetail> =
        ResponseEntity
            .status(error.statusCode)
            .body(ProblemDetail.forStatusAndDetail(error.statusCode, error.reason ?: "Não foi possível atualizar as tags das capas."))
}
