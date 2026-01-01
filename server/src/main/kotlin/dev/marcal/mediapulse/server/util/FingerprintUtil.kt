package dev.marcal.mediapulse.server.util

import org.apache.commons.codec.digest.DigestUtils

object FingerprintUtil {
    fun normalize(input: String?): String =
        input
            ?.lowercase()
            ?.replace("""\s+""".toRegex(), " ")
            ?.replace("[^a-z0-9 ()\\-]".toRegex(), "")
            ?.trim()
            ?: ""

    fun artistFp(name: String) = DigestUtils.sha256Hex(normalize(name))

    fun albumFp(
        title: String,
        artistId: Long,
        year: Int?,
    ) = DigestUtils.sha256Hex("${normalize(title)}|$artistId|${year ?: ""}")

    // NEW: track identity independent of album
    fun trackFpV2(
        title: String,
        artistId: Long,
        durationMs: Int?,
    ) = DigestUtils.sha256Hex("${normalize(title)}|$artistId|${durationMs ?: ""}")

    fun trackFp(
        title: String,
        albumId: Long,
        disc: Int?,
        track: Int?,
    ) = DigestUtils.sha256Hex(
        "${normalize(title)}|$albumId|${disc ?: ""}|${track ?: ""}",
    )
}
