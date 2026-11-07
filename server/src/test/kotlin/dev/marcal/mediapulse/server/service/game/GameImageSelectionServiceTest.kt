package dev.marcal.mediapulse.server.service.game

import dev.marcal.mediapulse.server.api.games.GameImageSelectionRequest
import dev.marcal.mediapulse.server.integration.igdb.IgdbApiClient
import dev.marcal.mediapulse.server.integration.igdb.IgdbCoverResponse
import dev.marcal.mediapulse.server.integration.igdb.IgdbGameResponse
import dev.marcal.mediapulse.server.integration.steamgriddb.SteamGridDbApiClient
import dev.marcal.mediapulse.server.integration.steamgriddb.SteamGridDbGridResponse
import dev.marcal.mediapulse.server.model.game.Game
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.repository.crud.GameImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameImageSelectionServiceTest {
    private val games = mockk<GameRepository>()
    private val images = mockk<GameImageCrudRepository>(relaxed = true)
    private val igdb = mockk<IgdbApiClient>()
    private val steam = mockk<SteamGridDbApiClient>()
    private val storage = mockk<LibraryImageStorageService>()
    private val service = GameImageSelectionService(games, images, igdb, steam, storage)
    private val game = Game(id = 7, title = "Hades", originalTitle = "Hades", fingerprint = "hades", igdbId = "10", steamGridDbId = "20")

    init {
        every { games.findById(7) } returns Optional.of(game)
        every { steam.fetchImageOptions(20, false) } returns listOf(SteamGridDbGridResponse(1, "https://cdn.test/grid.jpg", 600, 900))
        every { steam.fetchImageOptions(20, true) } returns listOf(SteamGridDbGridResponse(2, "https://cdn.test/hero.jpg", 1920, 620))
        every { igdb.fetchImageOptions("10") } returns
            IgdbGameResponse(
                id = 10,
                cover = IgdbCoverResponse(imageId = "cover"),
                screenshots = listOf(IgdbCoverResponse(imageId = "screen")),
                artworks = listOf(IgdbCoverResponse(imageId = "art")),
            )
    }

    @Test
    fun `listing includes both providers and categories without storing files`() {
        val result = service.candidates(7)
        assertEquals(setOf("GRID", "HERO", "COVER", "SCREENSHOT", "ARTWORK"), result.images.map { it.kind }.toSet())
        verify(exactly = 0) { storage.downloadProviderImage(any()) }
        verify(exactly = 0) { images.insertIgnore(any(), any(), any(), any()) }
    }

    @Test
    fun `saves only selections and delegates repeated images to storage without touching cover`() {
        val options = service.candidates(7).images
        val grid = options.first { it.kind == "GRID" }
        val hero = options.first { it.kind == "HERO" }
        every { storage.downloadProviderImage(grid.url) } returns ImageContent(byteArrayOf(1), null)
        every { storage.downloadProviderImage(hero.url) } throws IllegalStateException("offline")
        val reference = "/api/image-assets/11111111-1111-1111-1111-111111111111/content"
        every { storage.saveGame(any(), 7, "Hades", any(), any()) } returns reference
        every { images.insertIgnore(7, reference, "GRID", false) } returnsMany listOf(1, 0)
        val result = service.save(7, GameImageSelectionRequest(listOf(grid.id, hero.id, grid.id)))
        assertEquals(listOf(grid.id), result.savedIds)
        assertEquals(listOf(hero.id), result.failedIds)
        assertEquals(listOf(grid.id), service.save(7, GameImageSelectionRequest(listOf(grid.id))).savedIds)
        verify(exactly = 2) { storage.downloadProviderImage(grid.url) }
        verify(exactly = 2) { storage.saveGame(any(), 7, "Hades", "STEAMGRIDDB", any()) }
        verify(exactly = 2) { images.insertIgnore(7, reference, "GRID", false) }
        verify(exactly = 0) { storage.downloadProviderImage(match { it != grid.url && it != hero.url }) }
        verify(exactly = 0) { images.markPrimaryForGame(any(), any()) }
        verify(exactly = 0) { games.save(any()) }
    }

    @Test
    fun `provider failure preserves available candidates and unknown selections never cause downloads`() {
        every { steam.fetchImageOptions(20, true) } throws IllegalStateException("offline")
        val result = service.candidates(7)
        assertEquals(4, result.images.size)
        assertEquals(1, result.warnings.size)
        val unknown = "a".repeat(64)
        assertEquals(listOf(unknown), service.save(7, GameImageSelectionRequest(listOf(unknown))).failedIds)
        verify(exactly = 0) { storage.downloadProviderImage(any()) }
        assertFailsWith<ResponseStatusException> { service.save(7, GameImageSelectionRequest(listOf("https://localhost/private"))) }
        assertFailsWith<ResponseStatusException> { service.save(7, GameImageSelectionRequest(emptyList())) }
        assertFailsWith<ResponseStatusException> { service.save(7, GameImageSelectionRequest(List(51) { unknown })) }
    }

    @Test
    fun `missing game returns not found`() {
        every { games.findById(8) } returns Optional.empty()
        assertEquals(404, assertFailsWith<ResponseStatusException> { service.candidates(8) }.statusCode.value())
    }
}
