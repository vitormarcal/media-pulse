package dev.marcal.mediapulse.server.util

object BookSlugUtil {
    fun from(
        bookId: Long,
        title: String,
    ): String {
        val safeTitle =
            title
                .lowercase()
                .replace("[^a-z0-9]+".toRegex(), "_")
                .trim('_')
                .take(40)

        return if (safeTitle.isBlank()) "$bookId" else "${bookId}_$safeTitle"
    }
}
