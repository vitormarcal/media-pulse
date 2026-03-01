package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchCreateRequest
import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieTitleSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTitleCrudRepository
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

class ManualMovieCatalogServiceTest {
    private lateinit var movieRepository: MovieRepository
    private lateinit var movieTitleCrudRepository: MovieTitleCrudRepository
    private lateinit var movieImageCrudRepository: MovieImageCrudRepository
    private lateinit var externalIdentifierRepository: ExternalIdentifierRepository
    private lateinit var tmdbApiClient: TmdbApiClient
    private lateinit var service: ManualMovieCatalogService

    @BeforeEach
    fun setUp() {
        movieRepository = mockk(relaxed = true)
        movieTitleCrudRepository = mockk(relaxed = true)
        movieImageCrudRepository = mockk(relaxed = true)
        externalIdentifierRepository = mockk(relaxed = true)
        tmdbApiClient = mockk(relaxed = true)

        service =
            ManualMovieCatalogService(
                movieRepository = movieRepository,
                movieTitleCrudRepository = movieTitleCrudRepository,
                movieImageCrudRepository = movieImageCrudRepository,
                externalIdentifierRepository = externalIdentifierRepository,
                tmdbApiClient = tmdbApiClient,
                tmdbProperties = TmdbProperties(imageBaseUrl = "https://image.tmdb.org"),
            )
    }

    @Test
    fun `nao duplica movie para fingerprint repetido`() {
        val movie = Movie(id = 11, originalTitle = "Dune", year = 2021, fingerprint = "fp")
        val request = ManualMovieWatchCreateRequest(Instant.parse("2026-03-01T10:00:00Z"), "Dune", 2021)

        every { externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any()) } returns null
        every { movieRepository.findByFingerprint(any()) } returnsMany listOf(null, movie)
        every { movieRepository.save(any()) } returns movie
        every { movieRepository.findById(11) } returns Optional.of(movie)
        every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs

        val first = service.resolveOrCreate(request)
        val second = service.resolveOrCreate(request)

        assertTrue(first.created)
        assertFalse(second.created)
        verify(exactly = 1) { movieRepository.save(any()) }
        verify(exactly = 2) {
            movieTitleCrudRepository.insertIgnore(
                11,
                "Dune",
                null,
                MovieTitleSource.MANUAL.name,
                true,
            )
        }
    }

    @Test
    fun `prioriza match por tmdbId antes do fingerprint`() {
        val existingMovie = Movie(id = 77, originalTitle = "Old", year = 2020, fingerprint = "fp-old")

        every {
            externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(EntityType.MOVIE, Provider.TMDB, "999")
        } returns ExternalIdentifier(entityType = EntityType.MOVIE, entityId = 77, provider = Provider.TMDB, externalId = "999")
        every { movieRepository.findById(any()) } returns Optional.of(existingMovie)
        every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { movieImageCrudRepository.existsByMovieIdAndIsPrimaryTrue(77) } returns true
        every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns
            ExternalIdentifier(
                entityType = EntityType.MOVIE,
                entityId = 77,
                provider = Provider.TMDB,
                externalId = "999",
            )

        val result =
            service.resolveOrCreate(
                ManualMovieWatchCreateRequest(
                    watchedAt = Instant.parse("2026-03-01T10:00:00Z"),
                    title = "Novo Titulo",
                    year = 2024,
                    tmdbId = "999",
                    imdbId = "tt123",
                ),
            )

        assertEquals(77, result.movie.id)
        verify(exactly = 0) { movieRepository.findByFingerprint(any()) }
        verify(exactly = 0) { movieRepository.save(any()) }
    }

    @Test
    fun `insere capa tmdb apenas quando nao existe imagem primaria`() {
        val movie = Movie(id = 51, originalTitle = "Dune", year = 2021, coverUrl = null, fingerprint = "fp")

        every { externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any()) } returns null
        every { movieRepository.findByFingerprint(any()) } returns movie
        every { movieRepository.findById(51) } returns Optional.of(movie.copy(coverUrl = "https://image.tmdb.org/t/p/w780/poster.jpg"))
        every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { movieImageCrudRepository.existsByMovieIdAndIsPrimaryTrue(51) } returns false
        every { tmdbApiClient.fetchPosterPath("222") } returns "/poster.jpg"
        every { movieImageCrudRepository.insertIgnore(any(), any(), any()) } just runs
        every {
            movieRepository.save(match { it.id == 51L && it.coverUrl == "https://image.tmdb.org/t/p/w780/poster.jpg" })
        } returns movie.copy(coverUrl = "https://image.tmdb.org/t/p/w780/poster.jpg")
        every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
        every { externalIdentifierRepository.save(any()) } returns mockk()

        val result =
            service.resolveOrCreate(
                ManualMovieWatchCreateRequest(
                    watchedAt = Instant.parse("2026-03-01T10:00:00Z"),
                    title = "Dune",
                    year = 2021,
                    tmdbId = "222",
                ),
            )

        assertTrue(result.coverAssigned)
        verify(exactly = 1) {
            movieImageCrudRepository.insertIgnore(
                51,
                "https://image.tmdb.org/t/p/w780/poster.jpg",
                true,
            )
        }
        verify(exactly = 1) { movieRepository.save(match { it.id == 51L && it.coverUrl != null }) }
    }
}
