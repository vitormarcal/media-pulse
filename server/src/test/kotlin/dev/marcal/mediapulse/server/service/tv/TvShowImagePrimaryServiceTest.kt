package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class TvShowImagePrimaryServiceTest {
    private val images = mockk<TvShowImageCrudRepository>(relaxed = true)
    private val shows = mockk<TvShowRepository>(relaxed = true)
    private val service = TvShowImagePrimaryService(images, shows)

    init {
        every { images.lockShowRowForPrimaryUpdate(9) } returns 9
    }

    @Test
    fun `existing cover survives automatic imports even without a primary row`() {
        every { shows.storedCover(9) } returns "chosen"
        assertNull(service.assign(9, listOf("new"), "new"))
        verifyOrder {
            images.lockShowRowForPrimaryUpdate(9)
            images.insertIgnore(9, "new", false)
            shows.storedCover(9)
        }
        verify(exactly = 0) { shows.updateCover(any(), any()) }
    }

    @Test
    fun `explicit cover selection sets primary under lock`() {
        assertEquals("new", service.assign(9, listOf("new"), "new", replaceCover = true))
        verifyOrder {
            images.lockShowRowForPrimaryUpdate(9)
            images.insertIgnore(9, "new", false)
            images.clearPrimaryForShow(9)
            images.markPrimaryForShow(9, "new")
            shows.updateCover(9, "new")
        }
    }
}
