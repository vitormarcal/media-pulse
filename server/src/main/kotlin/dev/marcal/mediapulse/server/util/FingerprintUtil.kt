package dev.marcal.mediapulse.server.util

import org.apache.commons.codec.digest.DigestUtils
import java.text.Normalizer
import java.util.Locale

object FingerprintUtil {
    fun normalizeIsbn(value: String?): String? {
        val trimmed = value?.trim()?.ifBlank { return null } ?: return null
        val normalized = trimmed.replace("-", "").uppercase()
        return normalized.ifBlank { null }
    }

    fun normalize(input: String?): String =
        input
            ?.let { Normalizer.normalize(it, Normalizer.Form.NFKD) }
            ?.replace("\\p{M}+".toRegex(), "")
            ?.lowercase(Locale.ROOT)
            ?.replace("""\s+""".toRegex(), " ")
            ?.replace("[^\\p{L}\\p{N} ()\\-]".toRegex(), "")
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

    fun movieFp(
        originalTitle: String,
        year: Int?,
    ) = DigestUtils.sha256Hex("movie|${normalize(originalTitle)}|${year ?: ""}")

    fun tvShowFp(
        originalTitle: String,
        year: Int?,
    ) = DigestUtils.sha256Hex("show|${normalize(originalTitle)}|${year ?: ""}")

    fun tvEpisodeFp(
        showId: Long,
        seasonNumber: Int?,
        episodeNumber: Int?,
        title: String,
    ) = DigestUtils.sha256Hex("episode|$showId|${seasonNumber ?: ""}|${episodeNumber ?: ""}|${normalize(title)}")
}
