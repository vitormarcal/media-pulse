package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test

class TvShowImagePrimaryServiceTest {
    private val tvShowImageCrudRepository = mockk<TvShowImageCrudRepository>(relaxed = true)
    private val service = TvShowImagePrimaryService(tvShowImageCrudRepository)

    @Test
    fun `should lock show row then switch primary image`() {
        service.setPrimaryForShow(
            showId = 9L,
            url = "/covers/plex/tv-shows/9/poster.jpg",
        )

        verifyOrder {
            tvShowImageCrudRepository.lockShowRowForPrimaryUpdate(9L)
            tvShowImageCrudRepository.clearPrimaryForShow(9L)
            tvShowImageCrudRepository.markPrimaryForShow(9L, "/covers/plex/tv-shows/9/poster.jpg")
        }
    }
}
