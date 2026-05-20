package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.RediscoveredAlbumResponse
import dev.marcal.mediapulse.server.repository.MusicQueryRepository
import dev.marcal.mediapulse.server.service.music.AlbumTermsService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MusicSummaryControllerTest {
    private val repository = mockk<MusicQueryRepository>(relaxed = true)
    private val albumTermsService = mockk<AlbumTermsService>(relaxed = true)
    private val controller = MusicSummaryController(repository, albumTermsService)

    @Test
    fun `rediscovered albums should delegate with normalized limit`() {
        val expected =
            listOf(
                RediscoveredAlbumResponse(
                    albumId = 10L,
                    albumTitle = "Vespertine",
                    artistId = 99L,
                    artistName = "Bjork",
                    year = 2001,
                    coverUrl = null,
                    historicalPlayCount = 12L,
                    recentPlayCount = 3L,
                    lastHistoricalPlay = Instant.parse("2025-12-01T10:00:00Z"),
                    firstRecentPlay = Instant.parse("2026-04-05T10:00:00Z"),
                    latestPlay = Instant.parse("2026-04-08T10:00:00Z"),
                    quietGapDays = 125L,
                ),
            )
        every { repository.getRediscoveredAlbums(1000) } returns expected

        val result = controller.rediscoveredAlbums(1200)

        assertEquals(expected, result)
        verify(exactly = 1) { repository.getRediscoveredAlbums(1000) }
    }

    @Test
    fun `rediscovered albums should reject non positive limit`() {
        assertFailsWith<IllegalArgumentException> {
            controller.rediscoveredAlbums(0)
        }
    }
}
