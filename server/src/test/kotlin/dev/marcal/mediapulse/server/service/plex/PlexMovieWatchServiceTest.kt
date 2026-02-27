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
            val persistedMovie =
                Movie(
                    id = 42,
                    originalTitle = "Eyes Wide Shut",
                    year = 1999,
                    description = "desc",
                    fingerprint = "fp",
                )

            every { movieRepository.findByFingerprint(any()) } returns null
            every { movieRepository.save(any()) } returns persistedMovie
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

            verify(exactly = 1) { movieRepository.save(match { it.slug == "eyes-wide-shut" }) }
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

    private fun moviePayload(
        event: String = "media.scrobble",
        type: String = "movie",
        lastViewedAt: Instant? = Instant.ofEpochSecond(1772082419),
    ): PlexWebhookPayload =
        PlexWebhookPayload(
            event = event,
            metadata =
                PlexWebhookPayload.PlexMetadata(
                    ratingKey = "3828",
                    slug = "eyes-wide-shut",
                    type = type,
                    title = "De Olhos Bem Fechados",
                    originalTitle = "Eyes Wide Shut",
                    year = 1999,
                    summary = "desc",
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
