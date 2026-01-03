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
        titleKey: String,
        artistId: Long,
        year: Int?,
    ) = DigestUtils.sha256Hex("album|$artistId|${year ?: ""}|$titleKey")

    fun trackFp(
        title: String,
        artistId: Long,
        durationMs: Int?,
    ) = DigestUtils.sha256Hex("${normalize(title)}|$artistId|${durationMs ?: ""}")
}
