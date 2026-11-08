package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import dev.marcal.mediapulse.server.service.movie.MovieImageAssignmentService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType

class PlexMovieArtworkServiceTest {
    private lateinit var plexApiClient: PlexApiClient
    private lateinit var imageStorageService: LibraryImageStorageService
    private lateinit var movieImageCrudRepository: MovieImageCrudRepository
    private lateinit var movieImagePrimaryService: MovieImageAssignmentService
    private lateinit var movieRepository: MovieRepository
    private lateinit var service: PlexMovieArtworkService

    @BeforeEach
    fun setUp() {
        plexApiClient = mockk(relaxed = true)
        imageStorageService = mockk(relaxed = true)
        movieImageCrudRepository = mockk(relaxed = true)
        movieRepository = mockk(relaxed = true)
        every { movieRepository.storedCover(any()) } returns null
        movieImagePrimaryService = MovieImageAssignmentService(movieRepository, movieImageCrudRepository)

        service =
            PlexMovieArtworkService(
                plexApi = plexApiClient,
                imageStorageService = imageStorageService,
                imageAssignment = movieImagePrimaryService,
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
                imageStorageService.saveMovie(
                    image = any(),
                    provider = "PLEX",
                    movieId = 10,
                    fileHint = any(),
                    title = any(),
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
            verify(exactly = 1) { movieRepository.updateCover(10, "/covers/plex/movies/10/poster.jpg") }
            verify(exactly = 1) { movieImageCrudRepository.markPrimaryForMovie(10, "/covers/plex/movies/10/poster.jpg") }
        }

    @Test
    fun `should use fallback thumb when image list is empty`() =
        runBlocking {
            val movie = Movie(id = 11, originalTitle = "Movie", year = 2000, description = null, coverUrl = null, fingerprint = "fp2")
            val imageContent = ImageContent("abc".toByteArray(), MediaType.IMAGE_JPEG)

            coEvery { plexApiClient.downloadImageContent(any()) } returns imageContent
            every {
                imageStorageService.saveMovie(
                    image = any(),
                    provider = "PLEX",
                    movieId = 11,
                    fileHint = any(),
                    title = any(),
                )
            } returns "/covers/plex/movies/11/cover.jpg"
            every { movieRepository.save(any()) } answers { firstArg() }

            service.ensureMovieImagesFromPlex(
                movie = movie,
                images = emptyList(),
                fallbackThumbPath = "/library/metadata/999/thumb/111",
            )

            verify(exactly = 1) { movieImageCrudRepository.insertIgnore(11, "/covers/plex/movies/11/cover.jpg", false) }
            verify(exactly = 1) { movieRepository.updateCover(11, "/covers/plex/movies/11/cover.jpg") }
            verify { movieRepository.updateCover(11, "/covers/plex/movies/11/cover.jpg") }
        }

    @Test
    fun `should preserve non plex cover when syncing artwork`() =
        runBlocking {
            val movie =
                Movie(
                    id = 58,
                    originalTitle = "Le Mépris",
                    year = 1963,
                    description = null,
                    coverUrl = "/covers/tmdb/movies/58/58_le_mepris_4310440ee5f7.jpg",
                    fingerprint = "fp58",
                )
            val imageContent = ImageContent("abc".toByteArray(), MediaType.IMAGE_JPEG)

            coEvery { plexApiClient.downloadImageContent(any()) } returns imageContent
            every {
                imageStorageService.saveMovie(
                    image = any(),
                    provider = "PLEX",
                    movieId = 58,
                    fileHint = any(),
                    title = any(),
                )
            } returnsMany
                listOf(
                    "/covers/plex/movies/58/58_le_mepris_background.jpg",
                    "/covers/plex/movies/58/58_le_mepris_poster.jpg",
                )

            every { movieRepository.storedCover(58) } returns movie.coverUrl
            service.ensureMovieImagesFromPlex(
                movie = movie,
                images =
                    listOf(
                        PlexMovieArtworkService.PlexMovieImageCandidate(url = "/library/metadata/58/art/1", isPoster = false),
                        PlexMovieArtworkService.PlexMovieImageCandidate(url = "/library/metadata/58/thumb/1", isPoster = true),
                    ),
                fallbackThumbPath = null,
            )

            verify(exactly = 2) { movieImageCrudRepository.insertIgnore(movieId = 58, url = any(), isPrimary = false) }
            verify(exactly = 0) { movieRepository.updateCover(any(), any()) }
            verify(exactly = 0) { movieRepository.save(any()) }
        }
}
