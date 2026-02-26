package dev.marcal.mediapulse.server.service.plex.import

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.integration.plex.dto.PlexGuid
import dev.marcal.mediapulse.server.integration.plex.dto.PlexLibrarySection
import dev.marcal.mediapulse.server.integration.plex.dto.PlexMovie
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTitleCrudRepository
import dev.marcal.mediapulse.server.service.plex.PlexMovieArtworkService
import io.mockk.MockKAnnotations
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PlexMovieImportServiceTest {
    private lateinit var plexApiClient: PlexApiClient
    private lateinit var movieRepository: MovieRepository
    private lateinit var movieTitleCrudRepository: MovieTitleCrudRepository
    private lateinit var externalIdentifierRepository: ExternalIdentifierRepository
    private lateinit var plexMovieArtworkService: PlexMovieArtworkService
    private lateinit var service: PlexMovieImportService

    @BeforeEach
    fun setUp() {
        MockKAnnotations.init(this)
        plexApiClient = mockk()
        movieRepository = mockk(relaxed = true)
        movieTitleCrudRepository = mockk(relaxed = true)
        externalIdentifierRepository = mockk(relaxed = true)
        plexMovieArtworkService = mockk(relaxed = true)

        service =
            PlexMovieImportService(
                plexApiClient = plexApiClient,
                movieRepository = movieRepository,
                movieTitleCrudRepository = movieTitleCrudRepository,
                externalIdentifierRepository = externalIdentifierRepository,
                plexMovieArtworkService = plexMovieArtworkService,
            )
    }

    @Test
    fun `should import all movies from plex sections`() =
        runBlocking {
            val section = PlexLibrarySection(key = "1", type = "movie")
            val movie =
                PlexMovie(
                    ratingKey = "3828",
                    title = "De Olhos Bem Fechados",
                    originalTitle = "Eyes Wide Shut",
                    year = 1999,
                    summary = "desc",
                    thumb = "/library/metadata/3828/thumb/1771458357",
                    image =
                        listOf(
                            dev.marcal.mediapulse.server.integration.plex.dto.PlexImageAsset(
                                type = "coverPoster",
                                url = "/library/metadata/3828/thumb/1771458357",
                            ),
                            dev.marcal.mediapulse.server.integration.plex.dto.PlexImageAsset(
                                type = "background",
                                url = "/library/metadata/3828/art/1771458357",
                            ),
                        ),
                    guids = listOf(PlexGuid("tmdb://345"), PlexGuid("imdb://tt0120663")),
                )

            coEvery { plexApiClient.listMovieSections() } returns listOf(section)
            coEvery { plexApiClient.listMoviesPaged("1", 0, 200) } returns (listOf(movie) to 1)

            every { movieRepository.findByFingerprint(any()) } returns null
            every { movieRepository.save(any()) } returns
                Movie(id = 10, originalTitle = "Eyes Wide Shut", year = 1999, description = "desc", fingerprint = "fp")
            every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
            every { externalIdentifierRepository.save(any()) } returns mockk()
            coEvery { plexMovieArtworkService.ensureMovieImagesFromPlex(any(), any(), any()) } returns Unit

            val stats = service.importAllMovies(pageSize = 200)

            assertEquals(1, stats.moviesSeen)
            assertEquals(1, stats.moviesUpserted)

            coVerify(exactly = 1) { plexApiClient.listMovieSections() }
            coVerify(exactly = 1) { plexApiClient.listMoviesPaged("1", 0, 200) }
            coVerify(exactly = 1) { plexMovieArtworkService.ensureMovieImagesFromPlex(any(), any(), any()) }
            verify(exactly = 1) { movieRepository.save(any()) }
            verify(exactly = 2) {
                externalIdentifierRepository.save(
                    match {
                        it.entityType == EntityType.MOVIE &&
                            (it.provider == Provider.TMDB || it.provider == Provider.IMDB)
                    },
                )
            }
        }

    @Test
    fun `should update description when movie already exists and no ext ids are duplicated`() =
        runBlocking {
            val section = PlexLibrarySection(key = "1", type = "movie")
            val movie =
                PlexMovie(
                    ratingKey = "3828",
                    title = "De Olhos Bem Fechados",
                    originalTitle = "Eyes Wide Shut",
                    year = 1999,
                    summary = "updated",
                    guids = listOf(PlexGuid("tmdb://345")),
                )

            coEvery { plexApiClient.listMovieSections() } returns listOf(section)
            coEvery { plexApiClient.listMoviesPaged("1", 0, 200) } returns (listOf(movie) to 1)

            val existing = Movie(id = 10, originalTitle = "Eyes Wide Shut", year = 1999, description = null, fingerprint = "fp")
            every { movieRepository.findByFingerprint(any()) } returns existing
            every { movieRepository.save(any()) } returns existing.copy(description = "updated")
            every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { externalIdentifierRepository.findByProviderAndExternalId(Provider.TMDB, "345") } returns mockk()
            coEvery { plexMovieArtworkService.ensureMovieImagesFromPlex(any(), any(), any()) } returns Unit

            val stats = service.importAllMovies(pageSize = 200)

            assertEquals(1, stats.moviesSeen)
            assertEquals(1, stats.moviesUpserted)
            coVerify(exactly = 1) { plexMovieArtworkService.ensureMovieImagesFromPlex(any(), any(), any()) }
            verify(exactly = 1) { movieRepository.save(match { it.description == "updated" }) }
            verify(exactly = 0) { externalIdentifierRepository.save(any()) }
        }
}
