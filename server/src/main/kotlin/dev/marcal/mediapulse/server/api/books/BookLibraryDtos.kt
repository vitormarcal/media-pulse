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
