package dev.marcal.mediapulse.server.util

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class BookSlugUtilTest {
    @Test
    fun `from should normalize title with same rules as book cover path`() {
        val slug = BookSlugUtil.from(52L, "  The Dispossessed!  ")
        assertEquals("52_the_dispossessed", slug)
    }

    @Test
    fun `from should trim and limit normalized title to 40 characters`() {
        val slug = BookSlugUtil.from(10L, "A".repeat(60))
        assertEquals("10_${"a".repeat(40)}", slug)
    }

    @Test
    fun `from should fallback to id when title normalizes to blank`() {
        val slug = BookSlugUtil.from(7L, "!!!@@@")
        assertEquals("7", slug)
    }

    @Test
    fun `from should remove accents instead of replacing letters`() {
        val slug = BookSlugUtil.from(257L, "Blade - A Lâmina do Imortal, Volume 05")
        assertEquals("257_blade_a_lamina_do_imortal_volume_05", slug)
    }
}
