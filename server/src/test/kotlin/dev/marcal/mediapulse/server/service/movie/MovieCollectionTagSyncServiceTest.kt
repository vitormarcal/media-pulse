package dev.marcal.mediapulse.server.service.movie

import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MovieCollectionTagSyncServiceTest {
    @Test
    fun `names normalize unicode whitespace and deduplicate case without truncation`() {
        assertEquals(
            listOf("Mistborn", "Série um"),
            MovieCollectionTagSyncService.tags(listOf("  Mistborn ", "mistborn", "Série\u2003um", " ")),
        )
        assertEquals(emptyList(), MovieCollectionTagSyncService.tags(emptyList()))
        assertFailsWith<IllegalArgumentException> { MovieCollectionTagSyncService.tags(listOf("x".repeat(49))) }
        assertFailsWith<IllegalArgumentException> { MovieCollectionTagSyncService.tags((1..9).map { "Series $it" }) }
        assertEquals(48, MovieCollectionTagSyncService.tags(listOf("😀".repeat(24))).single().length)
        assertFailsWith<IllegalArgumentException> { MovieCollectionTagSyncService.tags(listOf("😀".repeat(25))) }
    }
}
