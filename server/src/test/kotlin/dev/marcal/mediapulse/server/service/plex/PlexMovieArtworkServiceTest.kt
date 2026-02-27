package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import dev.marcal.mediapulse.server.service.movie.MovieImagePrimaryService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import kotlin.test.assertEquals

class PlexMovieArtworkServiceTest {
    private lateinit var plexApiClient: PlexApiClient
    private lateinit var imageStorageService: ImageStorageService
    private lateinit var movieImageCrudRepository: MovieImageCrudRepository
    private lateinit var movieImagePrimaryService: MovieImagePrimaryService
    private lateinit var movieRepository: MovieRepository
    private lateinit var service: PlexMovieArtworkService

    @BeforeEach
    fun setUp() {
        plexApiClient = mockk(relaxed = true)
        imageStorageService = mockk(relaxed = true)
        movieImageCrudRepository = mockk(relaxed = true)
        movieImagePrimaryService = mockk(relaxed = true)
        movieRepository = mockk(relaxed = true)

        service =
            PlexMovieArtworkService(
                plexApi = plexApiClient,
                imageStorageService = imageStorageService,
                movieImageCrudRepository = movieImageCrudRepository,
                movieImagePrimaryService = movieImagePrimaryService,
                movieRepository = movieRepository,
            )
    }

    @Test
    fun `should save all images and set poster as primary`() =
        runBlocking {
            val movie =
                Movie(id = 10, originalTitle = "Eyes Wide Shut", year = 1999, description = null, coverUrl = null, fingerprint = "fp")
            val imageContent = ImageContent("abc".toByteArray(), MediaType.IMAGE_JPEG)

            coEvery { plexApiClient.downloadImageContent(any()) } returns imageContent
            every {
                imageStorageService.saveImageForMovie(
                    image = any(),
                    provider = "PLEX",
                    movieId = 10,
                    fileNameHint = any(),
                )
            } returnsMany
                listOf(
                    "/covers/plex/movies/10/background.jpg",
                    "/covers/plex/movies/10/poster.jpg",
                )
            every { movieRepository.save(any()) } answers { firstArg() }

            val images =
                listOf(
                    PlexMovieArtworkService.PlexMovieImageCandidate(url = "/library/metadata/3828/art/1771458357", isPoster = false),
                    PlexMovieArtworkService.PlexMovieImageCandidate(url = "/library/metadata/3828/thumb/1771458357", isPoster = true),
                )

            service.ensureMovieImagesFromPlex(movie = movie, images = images, fallbackThumbPath = null)

            verify(exactly = 2) { movieImageCrudRepository.insertIgnore(movieId = 10, url = any(), isPrimary = false) }
            verify(exactly = 1) { movieImagePrimaryService.setPrimaryForMovie(10, "/covers/plex/movies/10/poster.jpg") }
            verify(exactly = 1) { movieRepository.save(match { it.coverUrl == "/covers/plex/movies/10/poster.jpg" }) }
        }

    @Test
    fun `should use fallback thumb when image list is empty`() =
        runBlocking {
            val movie = Movie(id = 11, originalTitle = "Movie", year = 2000, description = null, coverUrl = null, fingerprint = "fp2")
            val imageContent = ImageContent("abc".toByteArray(), MediaType.IMAGE_JPEG)

            coEvery { plexApiClient.downloadImageContent(any()) } returns imageContent
            every {
                imageStorageService.saveImageForMovie(
                    image = any(),
                    provider = "PLEX",
                    movieId = 11,
                    fileNameHint = any(),
                )
            } returns "/covers/plex/movies/11/cover.jpg"
            every { movieRepository.save(any()) } answers { firstArg() }

            service.ensureMovieImagesFromPlex(
                movie = movie,
                images = emptyList(),
                fallbackThumbPath = "/library/metadata/999/thumb/111",
            )

            verify(exactly = 1) { movieImageCrudRepository.insertIgnore(11, "/covers/plex/movies/11/cover.jpg", false) }
            verify(exactly = 1) { movieImagePrimaryService.setPrimaryForMovie(11, "/covers/plex/movies/11/cover.jpg") }
            io.mockk.slot<Movie>().also { slot ->
                verify(exactly = 1) { movieRepository.save(capture(slot)) }
                assertEquals("/covers/plex/movies/11/cover.jpg", slot.captured.coverUrl)
            }
        }
}
