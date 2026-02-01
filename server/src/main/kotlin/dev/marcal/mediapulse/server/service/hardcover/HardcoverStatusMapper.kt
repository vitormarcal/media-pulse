package dev.marcal.mediapulse.server.service.hardcover

import dev.marcal.mediapulse.server.model.book.BookReadStatus

object HardcoverStatusMapper {
    fun mapSlug(slug: String?): BookReadStatus =
        when (slug?.lowercase()) {
            "read" -> BookReadStatus.READ
            "currently-reading" -> BookReadStatus.CURRENTLY_READING
            "want-to-read" -> BookReadStatus.WANT_TO_READ
            "did-not-finish" -> BookReadStatus.DID_NOT_FINISH
            "paused" -> BookReadStatus.PAUSED
            else -> BookReadStatus.UNKNOWN
        }
}
