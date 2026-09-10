package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.repository.PersonFavoritesRepository
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test

class PersonFavoritesServiceTest {
    private val repository = mockk<PersonFavoritesRepository>(relaxed = true)
    private val service = PersonFavoritesService(repository)

    @Test
    fun `favorite and unfavorite preserve simple idempotent commands`() {
        service.favorite(42)
        service.unfavorite(42)

        verify { repository.setFavorite(42, true) }
        verify { repository.setFavorite(42, false) }
    }
}
