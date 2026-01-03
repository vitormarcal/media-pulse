package dev.marcal.mediapulse.server.util

import java.text.Normalizer
import java.util.Locale

object TitleKeyUtil {
    /**
     * Normalização leve:
     * - lower
     * - & -> and
     * - remove diacríticos (latin) via NFKD
     * - troca tudo que não é letra/dígito por espaço
     * - colapsa espaços
     *
     */
    fun albumTitleKey(raw: String): String {
        val s = raw.trim()
        if (s.isBlank()) return ""

        val replaced = s.replace("&", " and ")

        // remove diacríticos (e.g. "Alucinação" -> "Alucinacao")
        val noDiacritics =
            Normalizer
                .normalize(replaced, Normalizer.Form.NFKD)
                .replace(Regex("\\p{M}+"), "")

        // mantém letras/números de qualquer idioma; o resto vira espaço
        val cleaned =
            buildString(noDiacritics.length) {
                for (ch in noDiacritics.lowercase(Locale.ROOT)) {
                    append(
                        when {
                            ch.isLetterOrDigit() -> ch
                            else -> ' '
                        },
                    )
                }
            }

        // colapsa espaços
        return cleaned.trim().replace(Regex("\\s+"), " ")
    }
}
