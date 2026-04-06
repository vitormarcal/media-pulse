package dev.marcal.mediapulse.server.api.books

import java.time.Instant

data class AuthorDetailsResponse(
    val authorId: Long,
    val name: String,
    val booksCount: Long,
    val readsCount: Long,
    val finishedCount: Long,
    val currentlyReadingCount: Long,
    val lastFinishedAt: Instant?,
    val books: List<BookCardDto>,
    val recentReads: List<ReadCardDto>,
)
