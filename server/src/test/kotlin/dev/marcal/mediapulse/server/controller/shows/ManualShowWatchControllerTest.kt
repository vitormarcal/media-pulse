package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.shows.ExistingShowWatchCreateRequest
import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateRequest
import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateResponse
import dev.marcal.mediapulse.server.service.tv.ExistingShowWatchCreateFlowService
import dev.marcal.mediapulse.server.service.tv.ManualShowWatchCreateFlowService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals

class ManualShowWatchControllerTest {
    private val service = mockk<ManualShowWatchCreateFlowService>()
    private val existingShowWatchCreateFlowService = mockk<ExistingShowWatchCreateFlowService>()
    private val controller = ManualShowWatchController(service, existingShowWatchCreateFlowService)

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

    @Test
    fun `should delegate existing show manual watch creation to flow service`() {
        val request =
            ExistingShowWatchCreateRequest(
                watchedAt = Instant.parse("2024-01-03T14:35:00Z"),
                episodeTitle = "Episódio 1",
                seasonNumber = 1,
                episodeNumber = 1,
            )
        val expected =
            ManualShowWatchCreateResponse(
                showId = 17,
                title = "葬送のフリーレン",
                year = 2023,
                coverUrl = "/covers/plex/tv-shows/17/poster.jpg",
                episodeId = 1359,
                episodeTitle = "Episódio 1",
                seasonNumber = 1,
                episodeNumber = 1,
                watchedAt = request.watchedAt,
                source = "MANUAL",
                createdShow = false,
                createdEpisode = true,
                watchInserted = true,
                coverAssigned = false,
                externalIds = emptyList(),
            )

        every { existingShowWatchCreateFlowService.execute(17, request) } returns expected

        val response = controller.createExistingShowWatch(17, request)

        assertEquals(17, response.showId)
        assertEquals(1359, response.episodeId)
        assertEquals(false, response.createdShow)
        verify(exactly = 1) { existingShowWatchCreateFlowService.execute(17, request) }
    }
}
