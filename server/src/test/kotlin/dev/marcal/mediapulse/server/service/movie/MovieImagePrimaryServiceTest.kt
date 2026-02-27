package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import io.mockk.mockk
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test

class MovieImagePrimaryServiceTest {
    private val movieImageCrudRepository = mockk<MovieImageCrudRepository>(relaxed = true)
    private val service = MovieImagePrimaryService(movieImageCrudRepository)

    @Test
    fun `should lock movie row then switch primary image`() {
        service.setPrimaryForMovie(
            movieId = 9L,
            url = "/covers/plex/movies/9/poster.jpg",
        )

        verifyOrder {
            movieImageCrudRepository.lockMovieRowForPrimaryUpdate(9L)
            movieImageCrudRepository.clearPrimaryForMovie(9L)
            movieImageCrudRepository.markPrimaryForMovie(9L, "/covers/plex/movies/9/poster.jpg")
        }
    }
}
