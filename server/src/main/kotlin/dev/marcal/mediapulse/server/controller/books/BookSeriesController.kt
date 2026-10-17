package dev.marcal.mediapulse.server.controller.books

import dev.marcal.mediapulse.server.api.books.ApplyBookSeriesSuggestionRequest
import dev.marcal.mediapulse.server.api.books.BookGroupNameRequest
import dev.marcal.mediapulse.server.api.books.BookGroupOrderRequest
import dev.marcal.mediapulse.server.api.books.BookSeriesMetadataPreviewDto
import dev.marcal.mediapulse.server.api.books.BookSeriesMetadataRequest
import dev.marcal.mediapulse.server.service.books.BookSeriesService
import dev.marcal.mediapulse.server.service.books.BookSeriesSuggestionsService
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.PutMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException

@RestController
@RequestMapping("/api/books")
class BookSeriesController(
    private val service: BookSeriesService,
    private val suggestions: BookSeriesSuggestionsService,
) {
    @ExceptionHandler(ResponseStatusException::class)
    fun invalid(error: ResponseStatusException): ResponseEntity<ProblemDetail> =
        ResponseEntity.status(error.statusCode).body(
            ProblemDetail.forStatusAndDetail(error.statusCode, error.reason ?: "Não foi possível concluir a operação."),
        )

    @GetMapping("/series")
    fun series() = service.list(service.kind("series"))

    @GetMapping("/universes")
    fun universes() = service.list(service.kind("universes"))

    @PostMapping("/series")
    @ResponseStatus(HttpStatus.CREATED)
    fun createSeries(
        @RequestBody request: BookGroupNameRequest,
    ) = service.create(service.kind("series"), request.name, request.description)

    @PostMapping("/universes")
    @ResponseStatus(HttpStatus.CREATED)
    fun createUniverse(
        @RequestBody request: BookGroupNameRequest,
    ) = service.create(service.kind("universes"), request.name)

    @GetMapping("/{kind:series|universes}/{id}")
    fun details(
        @PathVariable kind: String,
        @PathVariable id: Long,
    ) = service.details(service.kind(kind), id)

    @PutMapping("/{kind:series|universes}/{id}")
    fun rename(
        @PathVariable kind: String,
        @PathVariable id: Long,
        @RequestBody request: BookGroupNameRequest,
    ) = service.update(service.kind(kind), id, request)

    @DeleteMapping("/{kind:series|universes}/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun delete(
        @PathVariable kind: String,
        @PathVariable id: Long,
    ) = service.delete(service.kind(kind), id)

    @PostMapping("/{kind:series|universes}/{id}/items/{childId}")
    fun add(
        @PathVariable kind: String,
        @PathVariable id: Long,
        @PathVariable childId: Long,
    ) = service.add(service.kind(kind), id, childId)

    @DeleteMapping("/{kind:series|universes}/{id}/items/{childId}")
    fun remove(
        @PathVariable kind: String,
        @PathVariable id: Long,
        @PathVariable childId: Long,
    ) = service.remove(service.kind(kind), id, childId)

    @PutMapping("/{kind:series|universes}/{id}/order")
    fun order(
        @PathVariable kind: String,
        @PathVariable id: Long,
        @RequestBody request: BookGroupOrderRequest,
    ) = service.order(service.kind(kind), id, request.ids)

    @PostMapping("/series/{id}/metadata/preview")
    suspend fun metadataPreview(
        @PathVariable id: Long,
    ) = BookSeriesMetadataPreviewDto(
        service.details(service.kind("series"), id),
        suggestions.previewSeries(id),
    )

    @PostMapping("/series/{id}/metadata/apply")
    suspend fun metadataApply(
        @PathVariable id: Long,
        @RequestBody request: BookSeriesMetadataRequest,
    ) = service.applyMetadata(id, suggestions.previewSeries(id), request.fields)

    @GetMapping("/{bookId}/series")
    fun forBook(
        @PathVariable bookId: Long,
    ) = service.forBook(bookId)

    @PostMapping("/{bookId}/series/suggestions")
    suspend fun preview(
        @PathVariable bookId: Long,
    ) = suggestions.preview(bookId)

    @PostMapping("/{bookId}/series/suggestions/apply")
    suspend fun apply(
        @PathVariable bookId: Long,
        @RequestBody request: ApplyBookSeriesSuggestionRequest,
    ) = service.apply(
        suggestions.preview(bookId).firstOrNull { it.hardcoverId == request.hardcoverId }
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Sugestão não encontrada"),
        request,
    )
}
