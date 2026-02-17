package dev.marcal.mediapulse.server.api.books

import java.time.Instant
import java.time.LocalDate

data class AuthorDto(
    val id: Long,
    val name: String,
)

data class EditionDto(
    val id: Long,
    val title: String?,
    val isbn10: String?,
    val isbn13: String?,
    val pages: Int?,
    val language: String?,
    val publisher: String?,
    val format: String?,
    val coverUrl: String?,
)

data class BookCardDto(
    val bookId: Long,
    val title: String,
    val coverUrl: String?,
    val releaseDate: LocalDate?,
    val rating: Double?,
    val reviewedAt: Instant?,
    val authors: List<AuthorDto>,
)

data class ReadCardDto(
    val readId: Long,
    val status: BookReadStatus,
    val startedAt: Instant?,
    val finishedAt: Instant?,
    val progressPct: Double?,
    val progressPages: Int?,
    val source: String,
    val book: BookCardDto,
    val edition: EditionDto?,
)

data class RangeDto(
    val start: Instant,
    val end: Instant,
)

data class YearStatsDto(
    val finishedCount: Long,
    val currentlyReadingCount: Long,
    val wantCount: Long,
    val didNotFinishCount: Long,
    val pausedCount: Long,
    val pagesFinished: Long?,
)

data class YearReadsResponse(
    val year: Int,
    val range: RangeDto,
    val currentlyReading: List<ReadCardDto>,
    val finished: List<ReadCardDto>,
    val paused: List<ReadCardDto>,
    val didNotFinish: List<ReadCardDto>,
    val wantToRead: List<ReadCardDto>,
    val unknown: List<ReadCardDto>,
    val stats: YearStatsDto,
)

data class BookDetailsResponse(
    val bookId: Long,
    val title: String,
    val description: String?,
    val coverUrl: String?,
    val releaseDate: LocalDate?,
    val rating: Double?,
    val reviewRaw: String?,
    val reviewedAt: Instant?,
    val authors: List<AuthorDto>,
    val editions: List<EditionDto>,
    val reads: List<ReadCardDto>,
)

data class BooksListResponse(
    val items: List<ReadCardDto>,
    val nextCursor: String?,
)

data class BooksSearchResponse(
    val books: List<BookCardDto>,
    val authors: List<AuthorDto>,
)

data class SummaryCountsDto(
    val finished: Long,
    val reading: Long,
    val want: Long,
    val dnf: Long,
    val paused: Long,
    val total: Long,
)

data class TopAuthorDto(
    val authorId: Long,
    val authorName: String,
    val finishedCount: Long,
)

data class BooksSummaryResponse(
    val range: RangeDto,
    val counts: SummaryCountsDto,
    val topAuthors: List<TopAuthorDto>,
)
