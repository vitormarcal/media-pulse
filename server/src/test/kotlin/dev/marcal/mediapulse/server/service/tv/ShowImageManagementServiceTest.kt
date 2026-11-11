package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.model.tv.TvShowImage
import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShowImageManagementServiceTest {
    private val shows = mockk<TvShowRepository>(relaxed = true)
    private val images = mockk<TvShowImageCrudRepository>(relaxed = true)
    private val service = ShowImageManagementService(shows, images)
    private val gallery = listOf(TvShowImage(1, 7, "old", isPrimary = true), TvShowImage(3, 7, "new"), TvShowImage(2, 7, "other"))

    init {
        every { images.lockShowRowForPrimaryUpdate(7) } returns 7
        every { shows.storedCover(7) } returns "old"
        every { images.findByShowIdOrderByIsPrimaryDescIdDesc(7) } returns gallery
    }

    @Test
    fun `existing image becomes canonical cover and unique primary without deleting links`() {
        assertEquals("new", service.update(7, "new", false).coverUrl)
        verify {
            images.clearPrimaryForShow(7)
            images.markPrimaryForShow(7, "new")
            shows.updateCover(7, "new")
        }
        verify(exactly = 0) { images.removeForShow(any(), any()) }
    }

    @Test
    fun `removing cover chooses first remaining in gallery order`() {
        assertEquals("new", service.update(7, "old", true).coverUrl)
        verify {
            images.removeForShow(7, "old")
            images.markPrimaryForShow(7, "new")
            shows.updateCover(7, "new")
        }
    }

    @Test
    fun `removing another image preserves cover`() {
        assertEquals("old", service.update(7, "new", true).coverUrl)
        verify { shows.updateCover(7, "old") }
    }

    @Test
    fun `removing final image clears canonical cover and primary`() {
        every { images.findByShowIdOrderByIsPrimaryDescIdDesc(7) } returns gallery.take(1)
        assertEquals(null, service.update(7, "old", true).coverUrl)
        verify { shows.updateCover(7, null) }
        verify(exactly = 0) { images.markPrimaryForShow(any(), any()) }
    }

    @Test
    fun `legacy cover without association can be removed or made primary`() {
        every { images.findByShowIdOrderByIsPrimaryDescIdDesc(7) } returns emptyList()
        assertEquals("old", service.update(7, "old", false).coverUrl)
        verify { images.insertIgnore(7, "old", false) }
        assertEquals(null, service.update(7, "old", true).coverUrl)
    }

    @Test
    fun `missing show and unlinked images never change data`() {
        assertFailsWith<ResponseStatusException> { service.update(7, "unlinked", false) }
        assertFailsWith<ResponseStatusException> { service.update(7, "unlinked", true) }
        every { images.lockShowRowForPrimaryUpdate(8) } returns null
        assertFailsWith<ResponseStatusException> { service.update(8, "old", true) }
        verify(exactly = 0) {
            images.removeForShow(any(), any())
            images.clearPrimaryForShow(any())
            shows.updateCover(any(), any())
        }
    }
}
