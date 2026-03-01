package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchIngestItemRequest
import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchIngestRequest
import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieTitleSource
import dev.marcal.mediapulse.server.model.movie.MovieWatchSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTitleCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieWatchCrudRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualMovieWatchIngestionServiceTest {
    private lateinit var movieRepository: MovieRepository
    private lateinit var movieTitleCrudRepository: MovieTitleCrudRepository
    private lateinit var movieWatchCrudRepository: MovieWatchCrudRepository
    private lateinit var movieImageCrudRepository: MovieImageCrudRepository
    private lateinit var externalIdentifierRepository: ExternalIdentifierRepository
    private lateinit var tmdbApiClient: TmdbApiClient
    private lateinit var service: ManualMovieWatchIngestionService

    @BeforeEach
    fun setUp() {
        movieRepository = mockk(relaxed = true)
        movieTitleCrudRepository = mockk(relaxed = true)
        movieWatchCrudRepository = mockk(relaxed = true)
        movieImageCrudRepository = mockk(relaxed = true)
        externalIdentifierRepository = mockk(relaxed = true)
        tmdbApiClient = mockk(relaxed = true)

        service =
            ManualMovieWatchIngestionService(
                movieRepository = movieRepository,
                movieTitleCrudRepository = movieTitleCrudRepository,
                movieWatchCrudRepository = movieWatchCrudRepository,
                movieImageCrudRepository = movieImageCrudRepository,
                externalIdentifierRepository = externalIdentifierRepository,
                tmdbApiClient = tmdbApiClient,
                tmdbProperties = TmdbProperties(imageBaseUrl = "https://image.tmdb.org"),
            )
    }

    @Test
    fun `nao duplica movie para itens repetidos no mesmo lote`() {
        val watchedAt = Instant.parse("2026-03-01T10:00:00Z")
        val movie = Movie(id = 11, originalTitle = "Dune", year = 2021, fingerprint = "fp")

        every {
            externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any())
        } returns null
        every { movieRepository.findByFingerprint(any()) } returnsMany listOf(null, movie)
        every { movieRepository.save(any()) } returns movie
        every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { movieWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
        every {
            movieWatchCrudRepository.existsByMovieIdAndSourceAndWatchedAt(
                11,
                MovieWatchSource.MANUAL,
                watchedAt,
            )
        } returnsMany listOf(false, true)
        every { movieImageCrudRepository.existsByMovieIdAndIsPrimaryTrue(any()) } returns true

        val response =
            service.ingest(
                ManualMovieWatchIngestRequest(
                    items =
                        listOf(
                            ManualMovieWatchIngestItemRequest(watchedAt = watchedAt, title = "Dune", year = 2021),
                            ManualMovieWatchIngestItemRequest(watchedAt = watchedAt, title = "Dune", year = 2021),
                        ),
                ),
            )

        assertEquals(2, response.items.size)
        assertTrue(response.items.first().created)
        assertFalse(response.items.last().created)
        assertTrue(response.items.first().watchInserted)
        assertFalse(response.items.last().watchInserted)
        verify(exactly = 1) { movieRepository.save(any()) }
        verify(exactly = 2) {
            movieWatchCrudRepository.insertIgnore(
                11,
                MovieWatchSource.MANUAL.name,
                watchedAt,
            )
        }
    }

    @Test
    fun `prioriza match por tmdbId e imdbId antes do fingerprint`() {
        val watchedAt = Instant.parse("2026-03-01T10:00:00Z")
        val existingMovie = Movie(id = 77, originalTitle = "Old", year = 2020, fingerprint = "fp-old")

        every {
            externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(EntityType.MOVIE, Provider.TMDB, "999")
        } returns ExternalIdentifier(entityType = EntityType.MOVIE, entityId = 77, provider = Provider.TMDB, externalId = "999")
        every { movieRepository.findById(77) } returns Optional.of(existingMovie)
        every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { movieWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
        every { movieWatchCrudRepository.existsByMovieIdAndSourceAndWatchedAt(any(), any(), any()) } returns false
        every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns
            ExternalIdentifier(
                entityType = EntityType.MOVIE,
                entityId = 77,
                provider = Provider.TMDB,
                externalId = "999",
            )
        every { movieImageCrudRepository.existsByMovieIdAndIsPrimaryTrue(77) } returns true

        val response =
            service.ingest(
                ManualMovieWatchIngestRequest(
                    items =
                        listOf(
                            ManualMovieWatchIngestItemRequest(
                                watchedAt = watchedAt,
                                title = "Novo Titulo",
                                year = 2024,
                                tmdbId = "999",
                                imdbId = "tt123",
                            ),
                        ),
                ),
            )

        assertEquals(77, response.items.first().movieId)
        verify(exactly = 0) { movieRepository.findByFingerprint(any()) }
        verify(exactly = 0) { movieRepository.save(any()) }
        verify(exactly = 1) {
            movieTitleCrudRepository.insertIgnore(
                77,
                "Novo Titulo",
                null,
                MovieTitleSource.MANUAL.name,
                true,
            )
        }
    }

    @Test
    fun `insere capa tmdb apenas quando filme nao possui imagem primaria`() {
        val watchedAt = Instant.parse("2026-03-01T10:00:00Z")
        val movie = Movie(id = 51, originalTitle = "Dune", year = 2021, coverUrl = null, fingerprint = "fp")

        every {
            externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any())
        } returns null
        every { movieRepository.findByFingerprint(any()) } returns movie
        every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { movieWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
        every { movieWatchCrudRepository.existsByMovieIdAndSourceAndWatchedAt(any(), any(), any()) } returns false
        every { movieImageCrudRepository.existsByMovieIdAndIsPrimaryTrue(51) } returns false
        every { tmdbApiClient.fetchPosterPath("222") } returns "/poster.jpg"
        every { movieImageCrudRepository.insertIgnore(any(), any(), any()) } just runs
        every { movieRepository.save(match { it.id == 51L && it.coverUrl == "https://image.tmdb.org/t/p/w780/poster.jpg" }) } returns
            movie.copy(coverUrl = "https://image.tmdb.org/t/p/w780/poster.jpg")
        every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
        every { externalIdentifierRepository.save(any()) } returns mockk()

        val response =
            service.ingest(
                ManualMovieWatchIngestRequest(
                    items =
                        listOf(
                            ManualMovieWatchIngestItemRequest(
                                watchedAt = watchedAt,
                                title = "Dune",
                                year = 2021,
                                tmdbId = "222",
                            ),
                        ),
                ),
            )

        assertTrue(response.items.first().coverAssigned)
        verify(exactly = 1) {
            movieImageCrudRepository.insertIgnore(
                51,
                "https://image.tmdb.org/t/p/w780/poster.jpg",
                true,
            )
        }
        verify(exactly = 1) { movieRepository.save(match { it.id == 51L && it.coverUrl != null }) }
    }

    @Test
    fun `nao insere capa quando ja existe primary`() {
        val watchedAt = Instant.parse("2026-03-01T10:00:00Z")
        val movie = Movie(id = 52, originalTitle = "Dune", year = 2021, fingerprint = "fp")

        every {
            externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any())
        } returns null
        every { movieRepository.findByFingerprint(any()) } returns movie
        every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { movieWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
        every { movieWatchCrudRepository.existsByMovieIdAndSourceAndWatchedAt(any(), any(), any()) } returns false
        every { movieImageCrudRepository.existsByMovieIdAndIsPrimaryTrue(52) } returns true
        every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
        every { externalIdentifierRepository.save(any()) } returns mockk()

        val response =
            service.ingest(
                ManualMovieWatchIngestRequest(
                    items =
                        listOf(
                            ManualMovieWatchIngestItemRequest(
                                watchedAt = watchedAt,
                                title = "Dune",
                                year = 2021,
                                tmdbId = "222",
                            ),
                        ),
                ),
            )

        assertFalse(response.items.first().coverAssigned)
        verify(exactly = 0) { tmdbApiClient.fetchPosterPath(any()) }
        verify(exactly = 0) { movieImageCrudRepository.insertIgnore(any(), any(), any()) }
    }
}
