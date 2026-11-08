package dev.marcal.mediapulse.server.service.game

import dev.marcal.mediapulse.server.model.game.Game
import dev.marcal.mediapulse.server.model.game.GameImage
import dev.marcal.mediapulse.server.repository.crud.GameImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class GameImageManagementServiceTest {
    private val games = mockk<GameRepository>(relaxed = true)
    private val images = mockk<GameImageCrudRepository>(relaxed = true)
    private val service = GameImageManagementService(games, images)
    private val game = Game(id = 7, title = "Jogo", originalTitle = "Jogo", fingerprint = "game", coverUrl = "old")
    private val gallery = listOf(GameImage(1, 7, "old", isPrimary = true), GameImage(3, 7, "new"), GameImage(2, 7, "other"))

    init {
        every { images.lockGameRowForPrimaryUpdate(7) } returns 7
        every { games.findById(7) } returns Optional.of(game)
        every { images.findByGameIdOrderByIsPrimaryDescIdDesc(7) } returns gallery
    }

    @Test
    fun `existing image becomes canonical cover and unique primary without deleting links`() {
        assertEquals("new", service.update(7, "new", false).coverUrl)
        verify {
            images.clearPrimaryForGame(7)
            images.markPrimaryForGame(7, "new")
            games.updateCover(7, "new")
        }
        verify(exactly = 0) { images.removeForGame(any(), any()) }
    }

    @Test
    fun `removing cover chooses first remaining in gallery order`() {
        assertEquals("new", service.update(7, "old", true).coverUrl)
        verify {
            images.removeForGame(7, "old")
            images.markPrimaryForGame(7, "new")
            games.updateCover(7, "new")
        }
    }

    @Test
    fun `removing another image preserves cover`() {
        assertEquals("old", service.update(7, "new", true).coverUrl)
        verify { games.updateCover(7, "old") }
    }

    @Test
    fun `removing final image clears canonical cover and primary`() {
        every { images.findByGameIdOrderByIsPrimaryDescIdDesc(7) } returns gallery.take(1)
        assertEquals(null, service.update(7, "old", true).coverUrl)
        verify { games.updateCover(7, null) }
        verify(exactly = 0) { images.markPrimaryForGame(any(), any()) }
    }

    @Test
    fun `legacy cover without association can be removed or made primary`() {
        every { images.findByGameIdOrderByIsPrimaryDescIdDesc(7) } returns emptyList()
        assertEquals("old", service.update(7, "old", false).coverUrl)
        verify { images.insertIgnore(7, "old", "COVER", false) }
        assertEquals(null, service.update(7, "old", true).coverUrl)
    }

    @Test
    fun `missing game and unlinked images never change data`() {
        assertFailsWith<ResponseStatusException> { service.update(7, "unlinked", false) }
        assertFailsWith<ResponseStatusException> { service.update(7, "unlinked", true) }
        every { images.lockGameRowForPrimaryUpdate(8) } returns null
        assertFailsWith<ResponseStatusException> { service.update(8, "old", true) }
        verify(exactly = 0) {
            images.removeForGame(any(), any())
            images.clearPrimaryForGame(any())
            games.updateCover(any(), any())
        }
    }
}
