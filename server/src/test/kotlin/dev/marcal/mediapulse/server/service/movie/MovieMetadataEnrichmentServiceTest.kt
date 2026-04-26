package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentApplyMode
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentApplyRequest
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentField
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentImageSelectionRequest
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentPreviewRequest
import dev.marcal.mediapulse.server.api.movies.MovieExternalIdDto
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MovieMetadataEnrichmentServiceTest {
    private val movieQueryRepository = mockk<MovieQueryRepository>()
    private val movieRepository = mockk<MovieRepository>()
    private val externalIdentifierRepository = mockk<ExternalIdentifierRepository>()
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>()
    private val movieTermsService = mockk<MovieTermsService>(relaxed = true)
    private val movieCreditsService = mockk<MovieCreditsService>(relaxed = true)
    private val movieCompaniesService = mockk<MovieCompaniesService>(relaxed = true)

    private val service =
        MovieMetadataEnrichmentService(
            movieQueryRepository = movieQueryRepository,
            movieRepository = movieRepository,
            externalIdentifierRepository = externalIdentifierRepository,
            manualMovieCatalogService = manualMovieCatalogService,
            movieTermsService = movieTermsService,
            movieCreditsService = movieCreditsService,
            movieCompaniesService = movieCompaniesService,
        )

    @Test
    fun `marca campos faltantes por padrao no preview`() {
        every { movieQueryRepository.getMovieDetails(9) } returns
            MovieDetailsResponse(
                movieId = 9,
                title = "Dune",
                originalTitle = "Dune",
                slug = "dune",
                year = null,
                description = null,
                coverUrl = null,
                images = emptyList(),
                watches = emptyList(),
                externalIds = emptyList(),
            )
        every {
            manualMovieCatalogService.fetchTmdbMovieSnapshot("438631")
        } returns
            ManualMovieCatalogService.TmdbMovieSnapshot(
                tmdbId = "438631",
                title = "Dune",
                originalTitle = "Dune",
                imdbId = "tt1160419",
                overview = "Paul Atreides accepts his destiny.",
                releaseYear = 2021,
                posterPath = "/poster.jpg",
                backdropPath = "/backdrop.jpg",
                posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
                backdropUrl = "https://image.tmdb.org/t/p/w780/backdrop.jpg",
            )
        every {
            manualMovieCatalogService.buildTmdbImageCandidates(any())
        } returns
            listOf(
                ManualMovieCatalogService.TmdbImageCandidate(
                    key = "poster",
                    label = "Poster",
                    path = "/poster.jpg",
                    imageUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
                    suggestedAsPrimary = true,
                ),
                ManualMovieCatalogService.TmdbImageCandidate(
                    key = "backdrop",
                    label = "Backdrop",
                    path = "/backdrop.jpg",
                    imageUrl = "https://image.tmdb.org/t/p/w780/backdrop.jpg",
                    suggestedAsPrimary = false,
                ),
            )

        val response = service.preview(9, MovieEnrichmentPreviewRequest(tmdbId = "438631"))

        assertTrue(response.fields.first { it.field == MovieEnrichmentField.YEAR }.selectedByDefault)
        assertTrue(response.fields.first { it.field == MovieEnrichmentField.DESCRIPTION }.selectedByDefault)
        assertTrue(response.images.selectedByDefault)
        assertEquals(listOf("poster", "backdrop"), response.images.candidates.map { it.key })
    }

    @Test
    fun `aplica campos selecionados e vincula ids externos`() {
        val movie =
            Movie(id = 9, originalTitle = "Dune", year = null, description = null, slug = "dune", coverUrl = null, fingerprint = "fp")

        every { movieRepository.findById(9) } returnsMany
            listOf(Optional.of(movie), Optional.of(movie.copy(year = 2021, description = "Paul Atreides accepts his destiny.")))
        every { movieRepository.save(any()) } answers { firstArg() }
        every { movieQueryRepository.getMovieDetails(9) } returns
            MovieDetailsResponse(
                movieId = 9,
                title = "Dune",
                originalTitle = "Dune",
                slug = "dune",
                year = null,
                description = null,
                coverUrl = null,
                images = emptyList(),
                watches = emptyList(),
                externalIds = listOf(MovieExternalIdDto(provider = "TMDB", externalId = "438631")),
            )
        every {
            manualMovieCatalogService.fetchTmdbMovieSnapshot("438631")
        } returns
            ManualMovieCatalogService.TmdbMovieSnapshot(
                tmdbId = "438631",
                title = "Dune",
                originalTitle = "Dune",
                imdbId = "tt1160419",
                overview = "Paul Atreides accepts his destiny.",
                releaseYear = 2021,
                posterPath = "/poster.jpg",
                backdropPath = null,
                posterUrl = "https://image.tmdb.org/t/p/w780/poster.jpg",
                backdropUrl = null,
            )
        every { manualMovieCatalogService.linkExternalIdIfAvailable(9, Provider.IMDB, "tt1160419") } just runs
        every {
            manualMovieCatalogService.assignSelectedTmdbImages(
                any(),
                any(),
                any(),
            )
        } returns ManualMovieCatalogService.TmdbImageAssignmentResult(insertedCount = 1, primaryImageUrl = "/covers/tmdb/dune.jpg")
        every { manualMovieCatalogService.assignTmdbCollection(any(), any()) } answers { firstArg() }
        every { externalIdentifierRepository.findByEntityTypeAndEntityId(EntityType.MOVIE, 9) } returns
            listOf(
                ExternalIdentifier(entityType = EntityType.MOVIE, entityId = 9, provider = Provider.IMDB, externalId = "tt1160419"),
                ExternalIdentifier(entityType = EntityType.MOVIE, entityId = 9, provider = Provider.TMDB, externalId = "438631"),
            )

        val response =
            service.apply(
                9,
                MovieEnrichmentApplyRequest(
                    tmdbId = "438631",
                    mode = MovieEnrichmentApplyMode.SELECTED,
                    fields =
                        listOf(
                            MovieEnrichmentField.YEAR,
                            MovieEnrichmentField.DESCRIPTION,
                            MovieEnrichmentField.IMDB_ID,
                            MovieEnrichmentField.IMAGES,
                        ),
                    imageSelection =
                        MovieEnrichmentImageSelectionRequest(
                            selectedKeys = listOf("poster"),
                            primaryKey = "poster",
                        ),
                ),
            )

        assertEquals(
            listOf(MovieEnrichmentField.YEAR, MovieEnrichmentField.DESCRIPTION, MovieEnrichmentField.IMDB_ID, MovieEnrichmentField.IMAGES),
            response.appliedFields,
        )
        assertTrue(response.coverAssigned)
        verify(exactly = 1) { manualMovieCatalogService.linkExternalIdIfAvailable(9, Provider.IMDB, "tt1160419") }
        verify(exactly = 1) { manualMovieCatalogService.assignTmdbCollection(any(), any()) }
        verify(exactly = 1) {
            manualMovieCatalogService.assignSelectedTmdbImages(
                any(),
                any(),
                ManualMovieCatalogService.TmdbImageSelection(
                    selectedKeys = setOf("poster"),
                    primaryKey = "poster",
                ),
            )
        }
    }
}
