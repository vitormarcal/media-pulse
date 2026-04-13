package dev.marcal.mediapulse.server.api.books

import java.time.Instant

data class BookLibraryCardDto(
    val bookId: Long,
    val slug: String,
    val title: String,
    val coverUrl: String?,
    val authors: List<AuthorDto>,
    val readsCount: Long,
    val completedCount: Long,
    val currentStatus: BookReadStatus?,
    val activeProgressPct: Double?,
    val lastActivityAt: Instant?,
)

data class BooksLibraryResponse(
    val items: List<BookLibraryCardDto>,
    val nextCursor: String?,
)

data class BooksStatsResponse(
    val total: BooksTotalStatsDto,
    val unreadCount: Long,
    val years: List<BooksYearStatsDto>,
    val latestActivityAt: Instant?,
    val firstActivityAt: Instant?,
)

data class BooksTotalStatsDto(
    val booksCount: Long,
    val readsCount: Long,
    val completedCount: Long,
)

data class BooksYearStatsDto(
    val year: Int,
    val readsCount: Long,
    val uniqueBooksCount: Long,
    val finishedCount: Long,
    val currentlyReadingCount: Long,
    val wantCount: Long,
    val pausedCount: Long,
    val didNotFinishCount: Long,
)
