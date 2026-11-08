package dev.marcal.mediapulse.server.model.image

// Closed list: SQL identifiers never come from a request or database value.
enum class LocalImageTarget(
    val table: String,
    val column: String,
    val entityColumn: String = "id",
) {
    BOOK("books", "cover_url"),
    BOOK_EDITION("book_editions", "cover_url"),
    MAGAZINE("magazine_issues", "cover_url"),
    ARTWORK("artwork_images", "url", "artwork_id"),
    ARTIST("artwork_artists", "portrait_url"),
    MOVIE_COVER("movies", "cover_url"),
    MOVIE_IMAGE("movie_images", "url", "movie_id"),
    GAME_COVER("games", "cover_url"),
    GAME_IMAGE("game_images", "url", "game_id"),
}

data class LocalImageReference(
    val target: LocalImageTarget,
    val rowId: Long,
    val entityId: Long,
)

data class LocalImageCleanup(
    val localUrl: String,
    val remoteUrl: String,
    val sha256: String,
)
