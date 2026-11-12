package dev.marcal.mediapulse.server.controller.books

import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentApply
import dev.marcal.mediapulse.server.api.books.AuthorEnrichmentLookup
import dev.marcal.mediapulse.server.service.books.BookAuthorEnrichmentService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/books/authors/{authorId}/enrichment")
class BookAuthorEnrichmentController(
    private val service: BookAuthorEnrichmentService,
) {
    @PostMapping("/preview")
    suspend fun preview(
        @PathVariable authorId: Long,
        @RequestBody request: AuthorEnrichmentLookup,
    ) = service.preview(authorId, request.reference)

    @PostMapping
    fun apply(
        @PathVariable authorId: Long,
        @RequestBody request: AuthorEnrichmentApply,
    ) = service.apply(authorId, request)
}
