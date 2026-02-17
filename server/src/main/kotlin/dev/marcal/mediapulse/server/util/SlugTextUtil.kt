package dev.marcal.mediapulse.server.util

import java.text.Normalizer

object SlugTextUtil {
    fun normalize(
        value: String,
        maxLength: Int = 40,
    ): String {
        val withoutAccents =
            Normalizer
                .normalize(value, Normalizer.Form.NFD)
                .replace("\\p{M}+".toRegex(), "")

        return withoutAccents
            .lowercase()
            .replace("[^a-z0-9]+".toRegex(), "_")
            .trim('_')
            .take(maxLength)
    }
}
