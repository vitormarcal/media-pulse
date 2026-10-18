package dev.marcal.mediapulse.server.service.books

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class BookSeriesTagSyncServiceTest {
    @Test
    fun `names normalize unicode whitespace and deduplicate case without truncation`() {
        assertEquals(listOf("Mistborn", "Série um"), BookSeriesTagSyncService.tags(listOf("  Mistborn ", "mistborn", "Série\u2003um", " ")))
        assertEquals(emptyList(), BookSeriesTagSyncService.tags(emptyList()))
        assertFailsWith<IllegalArgumentException> { BookSeriesTagSyncService.tags(listOf("x".repeat(49))) }
        assertFailsWith<IllegalArgumentException> { BookSeriesTagSyncService.tags((1..9).map { "Series $it" }) }
        assertEquals(48, BookSeriesTagSyncService.tags(listOf("😀".repeat(24))).single().length)
        assertFailsWith<IllegalArgumentException> { BookSeriesTagSyncService.tags(listOf("😀".repeat(25))) }
    }
}
