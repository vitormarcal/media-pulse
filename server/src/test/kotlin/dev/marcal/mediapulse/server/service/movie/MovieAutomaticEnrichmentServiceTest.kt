package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.integration.tmdb.MovieTmdbResolution
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.MovieAutomaticEnrichmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional

class MovieAutomaticEnrichmentServiceTest {
    private val pendingRepository = mockk<MovieAutomaticEnrichmentRepository>()
    private val movieRepository = mockk<MovieRepository>()
    private val tmdbApiClient = mockk<TmdbApiClient>()
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>(relaxed = true)
    private val movieTermsService = mockk<MovieTermsService>(relaxed = true)
    private val movieCreditsService = mockk<MovieCreditsService>(relaxed = true)
    private val movieCompaniesService = mockk<MovieCompaniesService>(relaxed = true)
    private val service =
        MovieAutomaticEnrichmentService(
            pendingRepository,
            movieRepository,
            tmdbApiClient,
            manualMovieCatalogService,
            movieTermsService,
            movieCreditsService,
            movieCompaniesService,
        )

    @BeforeEach
    fun setUpCollection() {
        every { manualMovieCatalogService.fetchTmdbMovieSnapshot("603") } returns snapshot()
        every { pendingRepository.markCollectionComplete(any()) } returns 1
    }

    @Test
    fun `should assign collection even when other steps are already complete`() {
        val linked =
            movie(60, "603", null).copy(
                termsSyncedAt = Instant.now(),
                creditsSyncedAt = Instant.now(),
                companiesSyncedAt = Instant.now(),
            )
        every { movieRepository.findById(60) } returns Optional.of(linked)
        val snapshot =
            snapshot().copy(
                collection =
                    ManualMovieCatalogService.TmdbMovieCollectionSnapshot(
                        tmdbId = "2344",
                        name = "The Matrix Collection",
                        posterUrl = null,
                        backdropUrl = null,
                    ),
            )
        every { manualMovieCatalogService.fetchTmdbMovieSnapshot("603") } returns snapshot

        service.enrichMovie(60)

        verify { manualMovieCatalogService.assignTmdbCollection(linked, snapshot) }
        verify { pendingRepository.markCollectionComplete(60) }
    }

    @Test
    fun `should mark successful check without collection as complete`() {
        every { movieRepository.findById(61) } returns Optional.of(movie(61, "603", null))

        service.enrichMovie(61)

        verify { pendingRepository.markCollectionComplete(61) }
    }

    @Test
    fun `should retry unavailable collection without blocking other steps`() {
        every { movieRepository.findById(62) } returns Optional.of(movie(62, "603", null))
        every { manualMovieCatalogService.fetchTmdbMovieSnapshot("603") } returns null
        every { pendingRepository.markCollectionFailure(62, "TMDb movie unavailable") } returns 1

        service.enrichMovie(62)

        verify { pendingRepository.markCollectionFailure(62, "TMDb movie unavailable") }
        verify(exactly = 0) { pendingRepository.markCollectionComplete(any()) }
        verify { movieTermsService.syncFromTmdbIfLinked(62) }
        verify { movieCreditsService.syncFromTmdbIfLinked(62) }
        verify { movieCompaniesService.syncFromTmdbIfLinked(62) }
    }

    @Test
    fun `should skip checked collections and recent failures`() {
        every { movieRepository.findById(63) } returns Optional.of(movie(63, "603", null).copy(collectionCheckedAt = Instant.now()))
        every { movieRepository.findById(64) } returns Optional.of(movie(64, "603", null).copy(collectionSyncAttemptedAt = Instant.now()))

        service.enrichMovie(63)
        service.enrichMovie(64)

        verify(exactly = 0) { manualMovieCatalogService.fetchTmdbMovieSnapshot(any()) }
    }

    private fun snapshot() =
        ManualMovieCatalogService.TmdbMovieSnapshot(
            tmdbId = "603",
            title = "The Matrix",
            originalTitle = null,
            imdbId = null,
            overview = null,
            releaseYear = 1999,
            posterPath = null,
            backdropPath = null,
            posterUrl = null,
            backdropUrl = null,
        )

    @Test
    fun `should resolve IMDb and run every pending enrichment step`() {
        val unresolved = movie(id = 10, tmdbId = null, imdbId = "tt0133093")
        val linked = unresolved.copy(tmdbId = "603")
        every { movieRepository.findById(10) } returnsMany listOf(Optional.of(unresolved), Optional.of(linked))
        every { tmdbApiClient.resolveMovieByImdbId("tt0133093") } returns MovieTmdbResolution.Resolved("603")
        every { pendingRepository.clearTmdbResolutionResult(10) } returns 1

        service.enrichMovie(10)

        verify { manualMovieCatalogService.linkExternalIdIfAvailable(10, Provider.TMDB, "603") }
        verify { movieTermsService.syncFromTmdbIfLinked(10) }
        verify { movieCreditsService.syncFromTmdbIfLinked(10) }
        verify { movieCompaniesService.syncFromTmdbIfLinked(10) }
    }

    @Test
    fun `should only retry incomplete steps`() {
        val linked =
            movie(id = 20, tmdbId = "603", imdbId = "tt0133093").copy(
                termsSyncedAt = Instant.now(),
                companiesSyncedAt = Instant.now(),
            )
        every { movieRepository.findById(20) } returns Optional.of(linked)

        service.enrichMovie(20)

        verify(exactly = 0) { movieTermsService.syncFromTmdbIfLinked(any()) }
        verify(exactly = 1) { movieCreditsService.syncFromTmdbIfLinked(20) }
        verify(exactly = 0) { movieCompaniesService.syncFromTmdbIfLinked(any()) }
    }

    @Test
    fun `should block IMDb-only movie when TMDb has no match`() {
        val unresolved = movie(id = 30, tmdbId = null, imdbId = "tt-missing")
        every { movieRepository.findById(30) } returns Optional.of(unresolved)
        every { tmdbApiClient.resolveMovieByImdbId("tt-missing") } returns MovieTmdbResolution.NotFound
        every { pendingRepository.markTmdbResolutionNotFound(30) } returns 1

        service.enrichMovie(30)

        verify(exactly = 0) { manualMovieCatalogService.linkExternalIdIfAvailable(any(), any(), any()) }
        verify(exactly = 0) { movieCreditsService.syncFromTmdbIfLinked(any()) }
        verify { pendingRepository.markTmdbResolutionNotFound(30) }
    }

    @Test
    fun `should schedule retry when TMDb resolution fails temporarily`() {
        val unresolved = movie(id = 31, tmdbId = null, imdbId = "tt-temporary")
        every { movieRepository.findById(31) } returns Optional.of(unresolved)
        every { tmdbApiClient.resolveMovieByImdbId("tt-temporary") } returns
            MovieTmdbResolution.Failed("provider unavailable")
        every { pendingRepository.markTmdbResolutionFailure(31, "provider unavailable") } returns 1

        service.enrichMovie(31)

        verify { pendingRepository.markTmdbResolutionFailure(31, "provider unavailable") }
        verify(exactly = 0) { manualMovieCatalogService.linkExternalIdIfAvailable(any(), any(), any()) }
    }

    @Test
    fun `should record failed step and continue remaining enrichment`() {
        val linked = movie(id = 40, tmdbId = "603", imdbId = null)
        every { movieRepository.findById(40) } returns Optional.of(linked)
        every { movieTermsService.syncFromTmdbIfLinked(40) } throws IllegalStateException("terms unavailable")
        every { pendingRepository.markTermsFailure(40, "terms unavailable") } returns 1

        service.enrichMovie(40)

        verify { pendingRepository.markTermsFailure(40, "terms unavailable") }
        verify { movieCreditsService.syncFromTmdbIfLinked(40) }
        verify { movieCompaniesService.syncFromTmdbIfLinked(40) }
    }

    @Test
    fun `should not retry a recently failed step`() {
        val linked =
            movie(id = 50, tmdbId = "603", imdbId = null).copy(
                termsSyncAttemptedAt = Instant.now(),
                creditsSyncedAt = Instant.now(),
                companiesSyncedAt = Instant.now(),
            )
        every { movieRepository.findById(50) } returns Optional.of(linked)

        service.enrichMovie(50)

        verify(exactly = 0) { movieTermsService.syncFromTmdbIfLinked(any()) }
    }

    private fun movie(
        id: Long,
        tmdbId: String?,
        imdbId: String?,
    ) = Movie(
        id = id,
        originalTitle = "The Matrix",
        year = 1999,
        tmdbId = tmdbId,
        imdbId = imdbId,
        fingerprint = "movie-$id",
    )
}
