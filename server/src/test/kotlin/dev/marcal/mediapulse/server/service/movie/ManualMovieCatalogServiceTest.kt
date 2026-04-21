package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.integration.tmdb.TmdbImageClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieTitleSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCollectionCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTitleCrudRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualMovieCatalogServiceTest {
    private lateinit var movieRepository: MovieRepository
    private lateinit var movieTitleCrudRepository: MovieTitleCrudRepository
    private lateinit var movieCollectionCrudRepository: MovieCollectionCrudRepository
    private lateinit var movieImageCrudRepository: MovieImageCrudRepository
    private lateinit var externalIdentifierRepository: ExternalIdentifierRepository
    private lateinit var tmdbApiClient: TmdbApiClient
    private lateinit var tmdbImageClient: TmdbImageClient
    private lateinit var imageStorageService: ImageStorageService
    private lateinit var service: ManualMovieCatalogService

    @BeforeEach
    fun setUp() {
        movieRepository = mockk(relaxed = true)
        movieTitleCrudRepository = mockk(relaxed = true)
        movieCollectionCrudRepository = mockk(relaxed = true)
        movieImageCrudRepository = mockk(relaxed = true)
        externalIdentifierRepository = mockk(relaxed = true)
        tmdbApiClient = mockk(relaxed = true)
        tmdbImageClient = mockk(relaxed = true)
        imageStorageService = mockk(relaxed = true)
        every { movieRepository.save(any()) } answers { invocation.args[0] as Movie }

        service =
            ManualMovieCatalogService(
                movieRepository = movieRepository,
                movieTitleCrudRepository = movieTitleCrudRepository,
                movieCollectionCrudRepository = movieCollectionCrudRepository,
                movieImageCrudRepository = movieImageCrudRepository,
                externalIdentifierRepository = externalIdentifierRepository,
                tmdbApiClient = tmdbApiClient,
                tmdbImageClient = tmdbImageClient,
                imageStorageService = imageStorageService,
                tmdbProperties = TmdbProperties(imageBaseUrl = "https://image.tmdb.org"),
            )
    }

    @Test
    fun `nao duplica movie para fingerprint repetido`() {
        val movie = Movie(id = 11, originalTitle = "Dune", year = 2021, slug = "dune", fingerprint = "fp")
        val request = ManualMovieCatalogService.MovieCatalogUpsertRequest(title = "Dune", year = 2021)

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
        every { tmdbApiClient.fetchMovieDetails("999") } returns null
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
                ManualMovieCatalogService.MovieCatalogUpsertRequest(
                    title = "Novo Titulo",
                    year = 2024,
                    tmdbId = "999",
                    imdbId = "tt123",
                ),
            )

        assertEquals(77, result.movie.id)
        verify(exactly = 0) { movieRepository.findByFingerprint(any()) }
    }

    @Test
    fun `insere capa tmdb apenas quando nao existe imagem primaria`() {
        val movie = Movie(id = 51, originalTitle = "Dune", year = 2021, slug = "dune", coverUrl = null, fingerprint = "fp")

        every { externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any()) } returns null
        every { movieRepository.findByFingerprint(any()) } returns movie
        every { movieRepository.findById(51) } returns Optional.of(movie.copy(coverUrl = "/covers/tmdb/movies/51/51_dune_poster.jpg"))
        every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { movieImageCrudRepository.existsByMovieIdAndIsPrimaryTrue(51) } returns false
        every { tmdbApiClient.fetchMovieDetails("222") } returns
            TmdbApiClient.TmdbMovieDetails(
                title = "Dune",
                originalTitle = "Dune",
                imdbId = null,
                overview = "desc",
                releaseYear = 2021,
                posterPath = "/poster.jpg",
                backdropPath = "/backdrop.jpg",
            )
        every { tmdbImageClient.downloadImage("https://image.tmdb.org/t/p/w780/poster.jpg") } returns
            ImageContent(bytes = byteArrayOf(1, 2, 3), contentType = MediaType.IMAGE_JPEG)
        every { tmdbImageClient.downloadImage("https://image.tmdb.org/t/p/w780/backdrop.jpg") } returns
            ImageContent(bytes = byteArrayOf(4, 5, 6), contentType = MediaType.IMAGE_JPEG)
        every { imageStorageService.saveImageForMovie(any(), "TMDB", 51, any()) } returnsMany
            listOf(
                "/covers/tmdb/movies/51/51_dune_poster.jpg",
                "/covers/tmdb/movies/51/51_dune_backdrop.jpg",
            )
        every { movieImageCrudRepository.insertIgnore(any(), any(), any()) } just runs
        every { movieRepository.save(match { it.id == 51L && it.coverUrl == "/covers/tmdb/movies/51/51_dune_poster.jpg" }) } returns
            movie.copy(coverUrl = "/covers/tmdb/movies/51/51_dune_poster.jpg")
        every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
        every { externalIdentifierRepository.save(any()) } returns mockk()

        val result =
            service.resolveOrCreate(
                ManualMovieCatalogService.MovieCatalogUpsertRequest(
                    title = "Dune",
                    year = 2021,
                    tmdbId = "222",
                ),
            )

        assertTrue(result.coverAssigned)
        verify(exactly = 1) {
            movieImageCrudRepository.insertIgnore(
                51,
                "/covers/tmdb/movies/51/51_dune_poster.jpg",
                false,
            )
        }
        verify(exactly = 1) {
            movieImageCrudRepository.clearPrimaryForMovie(51)
        }
        verify(exactly = 1) {
            movieImageCrudRepository.markPrimaryForMovie(
                51,
                "/covers/tmdb/movies/51/51_dune_poster.jpg",
            )
        }
        verify(exactly = 1) { movieRepository.save(match { it.id == 51L && it.coverUrl != null }) }
    }

    @Test
    fun `preenche description e slug a partir do tmdb ao criar filme`() {
        val savedMovie =
            Movie(
                id = 90,
                originalTitle = "Dune: Part Two",
                year = 2024,
                description = "Paul Atreides unites with Chani.",
                slug = "dune-part-two",
                coverUrl = null,
                fingerprint = "fp",
            )

        every { externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any()) } returns null
        every { tmdbApiClient.fetchMovieDetails("693134") } returns
            TmdbApiClient.TmdbMovieDetails(
                title = "Dune: Part Two",
                originalTitle = "Dune: Part Two",
                imdbId = null,
                overview = "Paul Atreides unites with Chani.",
                releaseYear = 2024,
                posterPath = null,
                backdropPath = null,
            )
        every { movieRepository.findByFingerprint(any()) } returns null
        every { movieRepository.save(any()) } returns savedMovie
        every { movieRepository.findById(90) } returns Optional.of(savedMovie)
        every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
        every { externalIdentifierRepository.save(any()) } returns mockk()

        service.resolveOrCreate(
            ManualMovieCatalogService.MovieCatalogUpsertRequest(
                title = "Duna: Parte Dois",
                year = null,
                tmdbId = "693134",
            ),
        )

        verify(exactly = 1) {
            movieRepository.save(
                match {
                    it.originalTitle == "Dune: Part Two" &&
                        it.year == 2024 &&
                        it.description == "Paul Atreides unites with Chani." &&
                        it.slug == "dune-part-two"
                },
            )
        }
    }

    @Test
    fun `vincula colecao oficial tmdb ao criar filme`() {
        val savedMovie =
            Movie(
                id = 91,
                originalTitle = "The Matrix",
                year = 1999,
                description = "A hacker discovers reality.",
                slug = "the-matrix",
                coverUrl = null,
                fingerprint = "fp",
            )
        val linkedMovie = savedMovie.copy(collectionId = 12)

        every { externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any()) } returns null
        every { tmdbApiClient.fetchMovieDetails("603") } returns
            TmdbApiClient.TmdbMovieDetails(
                title = "The Matrix",
                originalTitle = "The Matrix",
                imdbId = "tt0133093",
                overview = "A hacker discovers reality.",
                releaseYear = 1999,
                posterPath = null,
                backdropPath = null,
                collection =
                    TmdbApiClient.TmdbMovieCollection(
                        tmdbId = "2344",
                        name = "The Matrix Collection",
                        posterPath = "/collection-poster.jpg",
                        backdropPath = "/collection-backdrop.jpg",
                    ),
            )
        every { movieRepository.findByFingerprint(any()) } returns null
        every { movieRepository.save(any()) } returnsMany listOf(savedMovie, linkedMovie)
        every { movieRepository.findById(91) } returns Optional.of(linkedMovie)
        every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { movieCollectionCrudRepository.upsertFromTmdb(any(), any(), any(), any()) } returns 12
        every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
        every { externalIdentifierRepository.save(any()) } returns mockk()

        val result =
            service.resolveOrCreate(
                ManualMovieCatalogService.MovieCatalogUpsertRequest(
                    title = "The Matrix",
                    tmdbId = "603",
                ),
            )

        assertEquals(12L, result.movie.collectionId)
        verify(exactly = 1) {
            movieCollectionCrudRepository.upsertFromTmdb(
                tmdbId = "2344",
                name = "The Matrix Collection",
                posterUrl = "https://image.tmdb.org/t/p/w780/collection-poster.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w780/collection-backdrop.jpg",
            )
        }
        verify(exactly = 1) { movieRepository.save(match { it.id == 91L && it.collectionId == 12L }) }
    }

    @Test
    fun `falha quando external id ja pertence a outro filme`() {
        val movie = Movie(id = 51, originalTitle = "Dune", year = 2021, slug = "dune", coverUrl = null, fingerprint = "fp")

        every { externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any()) } returns null
        every { movieRepository.findByFingerprint(any()) } returns movie
        every { movieRepository.findById(51) } returns Optional.of(movie)
        every { movieTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { tmdbApiClient.fetchMovieDetails("222") } returns
            TmdbApiClient.TmdbMovieDetails(
                title = "Dune",
                originalTitle = "Dune",
                imdbId = null,
                overview = "desc",
                releaseYear = 2021,
                posterPath = null,
                backdropPath = null,
            )
        every {
            externalIdentifierRepository.findByProviderAndExternalId(Provider.TMDB, "222")
        } returns
            ExternalIdentifier(
                entityType = EntityType.MOVIE,
                entityId = 99,
                provider = Provider.TMDB,
                externalId = "222",
            )

        val exception =
            kotlin
                .runCatching {
                    service.resolveOrCreate(
                        ManualMovieCatalogService.MovieCatalogUpsertRequest(
                            title = "Dune",
                            year = 2021,
                            tmdbId = "222",
                        ),
                    )
                }.exceptionOrNull() as ResponseStatusException

        assertEquals(HttpStatus.CONFLICT, exception.statusCode)
    }
}
