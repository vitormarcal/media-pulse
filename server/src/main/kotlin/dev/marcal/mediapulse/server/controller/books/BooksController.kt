package dev.marcal.mediapulse.server.controller.books

import dev.marcal.mediapulse.server.api.books.BookDetailsResponse
import dev.marcal.mediapulse.server.api.books.BookReadStatus
import dev.marcal.mediapulse.server.api.books.BooksListResponse
import dev.marcal.mediapulse.server.api.books.BooksSearchResponse
import dev.marcal.mediapulse.server.api.books.BooksSummaryResponse
import dev.marcal.mediapulse.server.api.books.YearReadsResponse
import dev.marcal.mediapulse.server.repository.BookQueryRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/api/books")
class BooksController(
    private val repository: BookQueryRepository,
) {
    @GetMapping("/year/{year}")
    fun yearReads(
        @PathVariable year: Int,
    ): YearReadsResponse = repository.getYearReads(year)

    @GetMapping("/{bookId}")
    fun bookDetails(
        @PathVariable bookId: Long,
    ): BookDetailsResponse = repository.getBookDetails(bookId)

    @GetMapping("/slug/{slug}")
    fun bookDetailsBySlug(
        @PathVariable slug: String,
    ): BookDetailsResponse = repository.getBookDetailsBySlug(slug)

    @GetMapping("/list")
    fun listReads(
        @RequestParam(required = false) status: String?,
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): BooksListResponse = repository.listReads(parseStatus(status), limit, cursor)

    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): BooksSearchResponse = repository.search(q, limit)

    @GetMapping("/summary")
    fun summary(
        @RequestParam(defaultValue = "month") range: String,
        @RequestParam(required = false) start: Instant?,
        @RequestParam(required = false) end: Instant?,
    ): BooksSummaryResponse {
        validateRange(range, start, end)
        return repository.summary(range, start, end)
    }

    private fun parseStatus(status: String?): BookReadStatus? {
        if (status.isNullOrBlank()) return null
        return try {
            BookReadStatus.valueOf(status.trim().uppercase())
        } catch (ex: IllegalArgumentException) {
            throw IllegalArgumentException("status inválido")
        }
    }

    private fun validateRange(
        range: String,
        start: Instant?,
        end: Instant?,
    ) {
        when (range.lowercase()) {
            "month", "year" -> Unit
            "custom" -> {
                if (start == null || end == null) {
                    throw IllegalArgumentException("range inválido")
                }
            }
            else -> throw IllegalArgumentException("range inválido")
        }
    }
}
