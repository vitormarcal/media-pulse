package dev.marcal.mediapulse.server.service.game

import dev.marcal.mediapulse.server.integration.igdb.IgdbApiClient
import dev.marcal.mediapulse.server.integration.igdb.IgdbGameResponse
import dev.marcal.mediapulse.server.integration.steamgriddb.SteamGridDbApiClient
import dev.marcal.mediapulse.server.integration.steamgriddb.SteamGridDbGameResponse
import dev.marcal.mediapulse.server.integration.steamgriddb.SteamGridDbGridResponse
import dev.marcal.mediapulse.server.model.game.Game
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.repository.crud.GameImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualGameCatalogServiceTest {
    private val gameRepository = mockk<GameRepository>()
    private val gameImageCrudRepository = mockk<GameImageCrudRepository>(relaxed = true)
    private val igdbApiClient = mockk<IgdbApiClient>()
    private val steamGridDbApiClient = mockk<SteamGridDbApiClient>()
    private val imageStorageService = mockk<LibraryImageStorageService>()
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

    @Test
    fun `SteamGridDB stores provider bytes through shared storage with game reference`() {
        var persisted = game()
        val image = ImageContent(byteArrayOf(1), null)
        val asset = "/api/image-assets/00000000-0000-0000-0000-000000000001/content"
        every { gameRepository.findByFingerprint(any()) } returns persisted
        every { steamGridDbApiClient.searchGames("Hades") } returns listOf(SteamGridDbGameResponse(5258, "Hades"))
        every { steamGridDbApiClient.fetchGrids(5258) } returns listOf(SteamGridDbGridResponse(1, "https://example.com/grid.png"))
        every { gameRepository.findBySteamGridDbId("5258") } returns null
        every { steamGridDbApiClient.downloadImage(any()) } returns image
        every { imageStorageService.saveGame(image, 42, "Hades", "STEAMGRIDDB", any()) } returns asset
        every { gameRepository.save(any()) } answers {
            persisted = firstArg()
            persisted
        }
        every { gameRepository.findById(42) } answers { Optional.of(persisted) }
        val result = service.resolveOrCreate(ManualGameCatalogService.GameCatalogUpsertRequest("Hades", 2020))
        assertTrue(result.coverAssigned)
        assertEquals(asset, result.game.coverUrl)
        assertEquals("5258", result.game.steamGridDbId)
        verify { gameImageCrudRepository.insertIgnore(42, asset, "GRID", false) }
    }

    @Test
    fun `IGDB fallback stores image bytes and failed uploads never persist provider URL`() {
        var persisted = game(igdbId = "1942")
        val snapshot = IgdbGameResponse(id = 1942, name = "Hades")
        val url = "https://example.com/igdb.png"
        val image = ImageContent(byteArrayOf(1), null)
        val asset = "/api/image-assets/00000000-0000-0000-0000-000000000001/content"
        every { igdbApiClient.fetchGame("1942") } returns snapshot
        every { igdbApiClient.buildCoverUrl(any()) } returns url
        every { gameRepository.findByIgdbId("1942") } returns persisted
        every { steamGridDbApiClient.searchGames("Hades") } returns emptyList()
        every { imageStorageService.downloadProviderImage(url) } returns image
        every { imageStorageService.saveGame(image, 42, "Hades", "IGDB", any()) } throws IllegalStateException("offline")
        every { gameRepository.findById(42) } answers { Optional.of(persisted) }
        every { gameRepository.save(any()) } answers {
            persisted = firstArg()
            persisted
        }
        val request = ManualGameCatalogService.GameCatalogUpsertRequest("Hades", 2020, "1942")
        assertFalse(service.resolveOrCreate(request).coverAssigned)
        verify(exactly = 0) { gameImageCrudRepository.insertIgnore(any(), any(), any(), any()) }
        every { imageStorageService.saveGame(image, 42, "Hades", "IGDB", any()) } returns asset
        val result = service.resolveOrCreate(request)
        assertTrue(result.coverAssigned)
        assertEquals(asset, result.game.coverUrl)
        verify { gameImageCrudRepository.insertIgnore(42, asset, "COVER", false) }
    }

    @Test
    fun `existing manual covers do not trigger provider image replacement`() {
        val existing = game().copy(coverUrl = "/covers/manual/games/42/cover.png")
        every { gameRepository.findByFingerprint(any()) } returns existing
        every { gameRepository.findById(42) } returns Optional.of(existing)
        assertFalse(service.resolveOrCreate(ManualGameCatalogService.GameCatalogUpsertRequest("Hades", 2020)).coverAssigned)
        verify(exactly = 0) { steamGridDbApiClient.searchGames(any()) }
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
