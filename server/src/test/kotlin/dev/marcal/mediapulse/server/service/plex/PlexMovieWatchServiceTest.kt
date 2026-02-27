package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieWatch
import dev.marcal.mediapulse.server.model.movie.MovieWatchSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTitleCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieWatchCrudRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlexMovieWatchServiceTest {
    private lateinit var movieRepository: MovieRepository
    private lateinit var movieTitleCrudRepository: MovieTitleCrudRepository
    private lateinit var movieWatchCrudRepository: MovieWatchCrudRepository
    private lateinit var externalIdentifierRepository: ExternalIdentifierRepository
    private lateinit var plexMovieArtworkService: PlexMovieArtworkService
    private lateinit var service: PlexMovieWatchService

    @BeforeEach
    fun setUp() {
        movieRepository = mockk(relaxed = true)
        movieTitleCrudRepository = mockk(relaxed = true)
        movieWatchCrudRepository = mockk(relaxed = true)
        externalIdentifierRepository = mockk(relaxed = true)
        plexMovieArtworkService = mockk(relaxed = true)

        service =
            PlexMovieWatchService(
                movieRepository = movieRepository,
                movieTitleCrudRepository = movieTitleCrudRepository,
                movieWatchCrudRepository = movieWatchCrudRepository,
                externalIdentifierRepository = externalIdentifierRepository,
                plexMovieArtworkService = plexMovieArtworkService,
            )
    }

    @Test
    fun `deve processar scrobble de filme e gravar watch e ids externos`() =
        runBlocking {
            val payload = moviePayload()
            val savedMovie = slot<Movie>()
            val persistedMovie =
                Movie(
                    id = 42,
                    originalTitle = "Eyes Wide Shut",
                    year = 1999,
                    description = "desc",
                    fingerprint = "fp",
                )

            every { movieRepository.findByFingerprint(any()) } returns null
            every { movieRepository.findByMovieTitleAndYear(any(), any()) } returns null
            every { movieRepository.save(capture(savedMovie)) } returns persistedMovie
            every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { movieWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
            every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
            every { externalIdentifierRepository.save(any()) } returns mockk()
            coEvery { plexMovieArtworkService.ensureMovieImagesFromPlex(any(), any(), any()) } returns Unit

            val result = service.processScrobble(payload)

            assertNotNull(result)
            assertEquals(42, result.movieId)
            assertEquals(MovieWatchSource.PLEX, result.source)
            assertEquals(Instant.ofEpochSecond(1772082419), result.watchedAt)
            assertEquals("eyes-wide-shut", savedMovie.captured.slug)

            verify(exactly = 1) { movieRepository.save(any()) }
            verify(exactly = 1) { movieWatchCrudRepository.insertIgnore(42, MovieWatchSource.PLEX.name, Instant.ofEpochSecond(1772082419)) }
            coVerify(exactly = 1) { plexMovieArtworkService.ensureMovieImagesFromPlex(any(), any(), any()) }
            verify(exactly = 1) {
                externalIdentifierRepository.save(
                    match {
                        it.entityType == EntityType.MOVIE &&
                            it.provider == Provider.TMDB &&
                            it.externalId == "345"
                    },
                )
            }
            verify(exactly = 1) {
                externalIdentifierRepository.save(
                    match { it.entityType == EntityType.MOVIE && it.provider == Provider.IMDB && it.externalId == "tt0120663" },
                )
            }
        }

    @Test
    fun `deve ignorar payload nao movie`() =
        runBlocking {
            val payload = moviePayload(type = "track")
            val result = service.processScrobble(payload)
            assertNull(result)
        }

    @Test
    fun `deve ignorar payload nao scrobble`() =
        runBlocking {
            val payload = moviePayload(event = "media.play")
            val result = service.processScrobble(payload)
            assertNull(result)
        }

    @Test
    fun `deve usar fallback watchedAt quando lastViewedAt vier nulo`() =
        runBlocking {
            val payload = moviePayload(lastViewedAt = null)
            val persistedMovie =
                Movie(
                    id = 7,
                    originalTitle = "Eyes Wide Shut",
                    year = 1999,
                    description = null,
                    fingerprint = "fp",
                )

            every { movieRepository.findByFingerprint(any()) } returns null
            every { movieRepository.findByMovieTitleAndYear(any(), any()) } returns null
            every { movieRepository.save(any()) } returns persistedMovie
            every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { movieWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
            every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
            every { externalIdentifierRepository.save(any()) } returns mockk()
            coEvery { plexMovieArtworkService.ensureMovieImagesFromPlex(any(), any(), any()) } returns Unit

            val before = Instant.now()
            val result: MovieWatch? = service.processScrobble(payload)
            val after = Instant.now()

            assertNotNull(result)
            assert(result.watchedAt.isAfter(before.minusSeconds(1)) && result.watchedAt.isBefore(after.plusSeconds(1)))
        }

    @Test
    fun `deve vincular scrobble ao filme existente via movie_titles e year`() =
        runBlocking {
            val payload =
                moviePayload(
                    title = "Esqueceram de Mim 2: Perdido em Nova York",
                    originalTitle = null,
                    titleSort = "Home Alone 2 Lost in New York",
                    year = 1992,
                    slug = null,
                    summary = "",
                )
            val existingMovie =
                Movie(
                    id = 99,
                    originalTitle = "Home Alone 2 Lost in New York",
                    year = 1992,
                    description = "canon",
                    slug = "home-alone-2-lost-in-new-york",
                    fingerprint = "fp-existing",
                )

            every { movieRepository.findByFingerprint(any()) } returns null
            every { movieRepository.findByMovieTitleAndYear("Home Alone 2 Lost in New York", 1992) } returns existingMovie
            every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { movieWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
            every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
            every { externalIdentifierRepository.save(any()) } returns mockk()
            coEvery { plexMovieArtworkService.ensureMovieImagesFromPlex(any(), any(), any()) } returns Unit

            val result = service.processScrobble(payload)

            assertNotNull(result)
            assertEquals(99, result.movieId)
            verify(exactly = 0) { movieRepository.save(any()) }
            verify(exactly = 1) { movieRepository.findByMovieTitleAndYear("Home Alone 2 Lost in New York", 1992) }
            verify(exactly = 1) { movieWatchCrudRepository.insertIgnore(99, MovieWatchSource.PLEX.name, Instant.ofEpochSecond(1772082419)) }
        }

    private fun moviePayload(
        event: String = "media.scrobble",
        type: String = "movie",
        title: String = "De Olhos Bem Fechados",
        titleSort: String? = null,
        originalTitle: String? = "Eyes Wide Shut",
        year: Int? = 1999,
        summary: String? = "desc",
        slug: String? = "eyes-wide-shut",
        lastViewedAt: Instant? = Instant.ofEpochSecond(1772082419),
    ): PlexWebhookPayload =
        PlexWebhookPayload(
            event = event,
            metadata =
                PlexWebhookPayload.PlexMetadata(
                    ratingKey = "3828",
                    slug = slug,
                    type = type,
                    title = title,
                    titleSort = titleSort,
                    originalTitle = originalTitle,
                    year = year,
                    summary = summary,
                    lastViewedAt = lastViewedAt,
                    thumb = "/library/metadata/3828/thumb/1771458357",
                    image =
                        listOf(
                            PlexWebhookPayload.PlexMetadata.PlexImageMetadata(
                                type = "coverPoster",
                                url = "/library/metadata/3828/thumb/1771458357",
                            ),
                            PlexWebhookPayload.PlexMetadata.PlexImageMetadata(
                                type = "background",
                                url = "/library/metadata/3828/art/1771458357",
                            ),
                        ),
                    guidList =
                        listOf(
                            PlexWebhookPayload.PlexMetadata.PlexGuidMetadata(id = "tmdb://345"),
                            PlexWebhookPayload.PlexMetadata.PlexGuidMetadata(id = "imdb://tt0120663"),
                        ),
                ),
        )
}
