package dev.marcal.mediapulse.server.service.game

import dev.marcal.mediapulse.server.api.games.ManualGameCatalogCreateRequest
import dev.marcal.mediapulse.server.integration.igdb.IgdbApiClient
import dev.marcal.mediapulse.server.model.game.Game
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class ManualGameCatalogCreateFlowServiceTest {
    private val manualGameCatalogService = mockk<ManualGameCatalogService>()
    private val igdbApiClient = mockk<IgdbApiClient>()
    private val service = ManualGameCatalogCreateFlowService(manualGameCatalogService, igdbApiClient)

    @Test
    fun `monta ids externos a partir das colunas do jogo`() {
        val request = ManualGameCatalogCreateRequest(title = "Hades", year = 2020, igdbId = "1942")
        val game =
            Game(
                id = 42,
                title = "Hades",
                originalTitle = "Hades",
                year = 2020,
                slug = "hades",
                fingerprint = "fp",
                igdbId = "1942",
                steamGridDbId = "5258",
            )
        every {
            manualGameCatalogService.resolveOrCreate(
                ManualGameCatalogService.GameCatalogUpsertRequest(title = "Hades", year = 2020, igdbId = "1942"),
            )
        } returns ManualGameCatalogService.GameCatalogResult(game, created = true, coverAssigned = false)

        val response = service.execute(request)

        assertEquals(listOf("IGDB", "STEAMGRIDDB"), response.externalIds.map { it.provider })
        assertEquals(listOf("1942", "5258"), response.externalIds.map { it.externalId })
    }
}
