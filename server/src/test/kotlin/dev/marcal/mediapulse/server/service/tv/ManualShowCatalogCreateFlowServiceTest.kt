package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ManualShowCatalogCreateRequest
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.tv.TvShow
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManualShowCatalogCreateFlowServiceTest {
    private val manualShowCatalogService = mockk<ManualShowCatalogService>()
    private val tmdbApiClient = mockk<TmdbApiClient>()
    private val showCreditsService = mockk<ShowCreditsService>(relaxed = true)

    private val service =
        ManualShowCatalogCreateFlowService(
            manualShowCatalogService = manualShowCatalogService,
            tmdbApiClient = tmdbApiClient,
            showCreditsService = showCreditsService,
        )

    @Test
    fun `monta resposta de catalogo e sincroniza creditos tmdb quando serie tem vinculo`() {
        val request = ManualShowCatalogCreateRequest(title = "Severance", year = 2022, tmdbId = "95396")
        val show =
            TvShow(
                id = 42,
                originalTitle = "Severance",
                year = 2022,
                slug = "severance",
                coverUrl = "/img.jpg",
                tmdbId = "95396",
                fingerprint = "fp",
            )

        every {
            manualShowCatalogService.resolveOrCreateCatalog(
                ManualShowCatalogService.ShowCatalogUpsertRequest(
                    title = "Severance",
                    year = 2022,
                    tmdbId = "95396",
                    tvdbId = null,
                    importEpisodes = true,
                ),
            )
        } returns
            ManualShowCatalogService.ShowCatalogResult(
                show = show,
                createdShow = true,
                coverAssigned = false,
                seasonsImported = 0,
                episodesImported = 0,
            )
        val response = service.execute(request)

        assertEquals(42, response.showId)
        assertEquals("severance", response.slug)
        assertTrue(response.createdShow)
        assertEquals("TMDB", response.externalIds.single().provider)
        verify(exactly = 1) { showCreditsService.syncFromTmdbIfLinked(42) }
    }

    @Test
    fun `monta sugestoes de catalogo a partir do tmdb`() {
        every { tmdbApiClient.searchShows("Severance") } returns
            listOf(
                TmdbApiClient.TmdbShowSearchItem(
                    tmdbId = "95396",
                    title = "Severance",
                    originalTitle = "Severance",
                    overview = "Mark leads a team.",
                    firstAirYear = 2022,
                    posterPath = "/poster.jpg",
                ),
            )
        every { manualShowCatalogService.buildTmdbImageUrl("/poster.jpg") } returns
            "https://image.tmdb.org/t/p/w780/poster.jpg"

        val response = service.suggest("Severance")

        assertEquals("Severance", response.query)
        assertEquals("95396", response.suggestions.single().tmdbId)
        assertEquals("https://image.tmdb.org/t/p/w780/poster.jpg", response.suggestions.single().posterUrl)
    }
}
