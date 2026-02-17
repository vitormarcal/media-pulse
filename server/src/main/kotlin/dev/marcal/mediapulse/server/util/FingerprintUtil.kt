package dev.marcal.mediapulse.server.util

import org.apache.commons.codec.digest.DigestUtils

object FingerprintUtil {
    fun normalizeIsbn(value: String?): String? {
        val trimmed = value?.trim()?.ifBlank { return null } ?: return null
        val normalized = trimmed.replace("-", "").uppercase()
        return normalized.ifBlank { null }
    }

    fun normalize(input: String?): String =
        input
            ?.lowercase()
            ?.replace("""\s+""".toRegex(), " ")
            ?.replace("[^a-z0-9 ()\\-]".toRegex(), "")
            ?.trim()
            ?: ""

    fun artistFp(name: String) = DigestUtils.sha256Hex(normalize(name))

    fun authorFp(name: String) = DigestUtils.sha256Hex(normalize(name))

    fun bookFp(title: String) = DigestUtils.sha256Hex(normalize(title))

    fun editionFp(
        isbn13: String?,
        hardcoverEditionId: Long?,
    ): String {
        val normalizedIsbn13 = normalizeIsbn(isbn13)
        val key =
            if (normalizedIsbn13 != null) {
                "isbn13:$normalizedIsbn13"
            } else {
                "hardcover_edition_id:${hardcoverEditionId ?: "unknown"}"
            }
        return DigestUtils.sha256Hex(key)
    }

    fun albumFp(
        titleKey: String,
        artistId: Long,
    ) = DigestUtils.sha256Hex("album|$artistId|$titleKey")

    fun trackFp(
        title: String,
        artistId: Long,
        durationMs: Int?,
    ) = DigestUtils.sha256Hex("${normalize(title)}|$artistId|${durationMs ?: ""}")
}
