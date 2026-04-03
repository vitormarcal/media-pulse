package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateRequest
import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateResponse
import dev.marcal.mediapulse.server.service.tv.ManualShowWatchCreateFlowService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class ManualShowWatchControllerTest {
    private val service = mockk<ManualShowWatchCreateFlowService>()
    private val controller = ManualShowWatchController(service)

    @Test
    fun `should delegate manual watch creation to flow service`() {
        val request =
            ManualShowWatchCreateRequest(
                watchedAt = Instant.parse("2026-03-01T10:00:00Z"),
                showTitle = "Severance",
                episodeTitle = "Good News About Hell",
                tmdbId = "95396",
                seasonNumber = 1,
                episodeNumber = 1,
            )
        val expected =
            ManualShowWatchCreateResponse(
                showId = 42,
                title = "Severance",
                year = 2022,
                coverUrl = "/img.jpg",
                episodeId = 99,
                episodeTitle = "Good News About Hell",
                seasonNumber = 1,
                episodeNumber = 1,
                watchedAt = request.watchedAt,
                source = "MANUAL",
                createdShow = false,
                createdEpisode = false,
                watchInserted = true,
                coverAssigned = false,
                externalIds = emptyList(),
            )

        every { service.execute(request) } returns expected

        val response = controller.createManualWatch(request)

        assertEquals(42, response.showId)
        assertEquals(99, response.episodeId)
        verify(exactly = 1) { service.execute(request) }
    }
}
