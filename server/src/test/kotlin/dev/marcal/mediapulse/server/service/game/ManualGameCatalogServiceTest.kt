package dev.marcal.mediapulse.server.service.game

import dev.marcal.mediapulse.server.integration.igdb.IgdbApiClient
import dev.marcal.mediapulse.server.integration.steamgriddb.SteamGridDbApiClient
import dev.marcal.mediapulse.server.integration.steamgriddb.SteamGridDbGameResponse
import dev.marcal.mediapulse.server.model.game.Game
import dev.marcal.mediapulse.server.repository.crud.GameImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ManualGameCatalogServiceTest {
    private val gameRepository = mockk<GameRepository>()
    private val gameImageCrudRepository = mockk<GameImageCrudRepository>(relaxed = true)
    private val igdbApiClient = mockk<IgdbApiClient>()
    private val steamGridDbApiClient = mockk<SteamGridDbApiClient>()
    private val imageStorageService = mockk<ImageStorageService>()
    private lateinit var service: ManualGameCatalogService

    @BeforeEach
    fun setUp() {
        service =
            ManualGameCatalogService(
                gameRepository = gameRepository,
                gameImageCrudRepository = gameImageCrudRepository,
                igdbApiClient = igdbApiClient,
                steamGridDbApiClient = steamGridDbApiClient,
                imageStorageService = imageStorageService,
            )
    }

    @Test
    fun `prioriza jogo existente pelo igdb id`() {
        val existing = game(igdbId = "1942")
        every { igdbApiClient.fetchGame("1942") } returns null
        every { gameRepository.findByIgdbId("1942") } returns existing
        every { steamGridDbApiClient.searchGames("Hades") } returns emptyList()
        every { gameRepository.findById(42) } returns Optional.of(existing)

        val result =
            service.resolveOrCreate(
                ManualGameCatalogService.GameCatalogUpsertRequest(title = "Hades", year = 2020, igdbId = "1942"),
            )

        assertEquals(42, result.game.id)
        assertFalse(result.created)
        verify(exactly = 0) { gameRepository.findByFingerprint(any()) }
    }

    @Test
    fun `persiste ids igdb e steamgriddb nas colunas do jogo`() {
        var persisted = game()
        every { igdbApiClient.fetchGame("1942") } returns null
        every { gameRepository.findByIgdbId("1942") } returns null
        every { gameRepository.findByFingerprint(any()) } returns persisted
        every { gameRepository.findBySteamGridDbId("5258") } returns null
        every { steamGridDbApiClient.searchGames("Hades") } returns listOf(SteamGridDbGameResponse(id = 5258, name = "Hades"))
        every { steamGridDbApiClient.fetchGrids(5258) } returns emptyList()
        every { gameRepository.save(any()) } answers {
            persisted = firstArg()
            persisted
        }
        every { gameRepository.findById(42) } answers { Optional.of(persisted) }

        val result =
            service.resolveOrCreate(
                ManualGameCatalogService.GameCatalogUpsertRequest(title = "Hades", year = 2020, igdbId = "1942"),
            )

        assertEquals("1942", result.game.igdbId)
        assertEquals("5258", result.game.steamGridDbId)
        verify(exactly = 2) { gameRepository.save(any()) }
    }

    private fun game(
        igdbId: String? = null,
        steamGridDbId: String? = null,
    ) = Game(
        id = 42,
        title = "Hades",
        originalTitle = "Hades",
        year = 2020,
        slug = "hades",
        fingerprint = "fp",
        igdbId = igdbId,
        steamGridDbId = steamGridDbId,
    )
}
