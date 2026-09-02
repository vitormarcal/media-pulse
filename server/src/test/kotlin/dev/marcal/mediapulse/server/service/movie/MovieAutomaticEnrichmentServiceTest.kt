package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.MovieAutomaticEnrichmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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

    @Test
    fun `should resolve IMDb and run every pending enrichment step`() {
        val unresolved = movie(id = 10, tmdbId = null, imdbId = "tt0133093")
        val linked = unresolved.copy(tmdbId = "603")
        every { movieRepository.findById(10) } returnsMany listOf(Optional.of(unresolved), Optional.of(linked))
        every { tmdbApiClient.findMovieTmdbIdByImdbId("tt0133093") } returns "603"
        every { pendingRepository.markTmdbResolutionChecked(10) } returns 1

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
    fun `should leave IMDb-only movie pending when TMDb cannot resolve it`() {
        val unresolved = movie(id = 30, tmdbId = null, imdbId = "tt-missing")
        every { movieRepository.findById(30) } returns Optional.of(unresolved)
        every { tmdbApiClient.findMovieTmdbIdByImdbId("tt-missing") } returns null
        every { pendingRepository.markTmdbResolutionChecked(30) } returns 1

        service.enrichMovie(30)

        verify(exactly = 0) { manualMovieCatalogService.linkExternalIdIfAvailable(any(), any(), any()) }
        verify(exactly = 0) { movieCreditsService.syncFromTmdbIfLinked(any()) }
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
