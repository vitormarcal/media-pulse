package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateRequest
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvShow
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ManualShowWatchCreateFlowServiceTest {
    private val manualShowCatalogService = mockk<ManualShowCatalogService>()
    private val manualShowWatchRegistrationService = mockk<ManualShowWatchRegistrationService>()

    private val service =
        ManualShowWatchCreateFlowService(
            manualShowCatalogService = manualShowCatalogService,
            manualShowWatchRegistrationService = manualShowWatchRegistrationService,
        )

    @Test
    fun `monta visualizacao com serie episodio e ids externos`() {
        val request =
            ManualShowWatchCreateRequest(
                watchedAt = Instant.parse("2026-03-01T10:00:00Z"),
                showTitle = "Severance",
                episodeTitle = "Good News About Hell",
                year = 2022,
                tmdbId = "95396",
                seasonNumber = 1,
                episodeNumber = 1,
            )

        val show =
            TvShow(
                id = 42,
                originalTitle = "Severance",
                year = 2022,
                coverUrl = "/img.jpg",
                tmdbId = "95396",
                fingerprint = "fp",
            )
        val episode =
            TvEpisode(id = 99, showId = 42, title = "Good News About Hell", seasonNumber = 1, episodeNumber = 1, fingerprint = "ep-fp")

        every { manualShowCatalogService.resolveOrCreate(request) } returns
            ManualShowCatalogService.ShowWatchCatalogResult(
                show = show,
                episode = episode,
                createdShow = false,
                createdEpisode = false,
                coverAssigned = false,
            )
        every { manualShowWatchRegistrationService.register(99, request.watchedAt) } returns false
        val response = service.execute(request)

        assertEquals(42, response.showId)
        assertEquals(99, response.episodeId)
        assertEquals("MANUAL", response.source)
        assertFalse(response.watchInserted)
        assertEquals(1, response.externalIds.size)
        assertEquals("TMDB", response.externalIds.first().provider)
    }
}
