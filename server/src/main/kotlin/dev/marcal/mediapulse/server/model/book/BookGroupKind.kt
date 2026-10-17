package dev.marcal.mediapulse.server.model.book

enum class BookGroupKind(
    val table: String,
    val members: String,
    val parentColumn: String,
    val childColumn: String,
    val childTable: String,
) {
    SERIES("book_series", "book_series_items", "series_id", "book_id", "books"),
    UNIVERSES("book_universes", "book_universe_series", "universe_id", "series_id", "book_series"),
}
