package dev.marcal.mediapulse.server.util

object BookSlugUtil {
    fun from(
        bookId: Long,
        title: String,
    ): String {
        val safeTitle = SlugTextUtil.normalize(title)

        return if (safeTitle.isBlank()) "$bookId" else "${bookId}_$safeTitle"
    }
}
