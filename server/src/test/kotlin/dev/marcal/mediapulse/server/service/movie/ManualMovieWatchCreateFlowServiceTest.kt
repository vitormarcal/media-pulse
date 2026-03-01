package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.ManualMovieWatchCreateRequest
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ManualMovieWatchCreateFlowServiceTest {
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>()
    private val manualMovieWatchRegistrationService = mockk<ManualMovieWatchRegistrationService>()
    private val externalIdentifierRepository = mockk<ExternalIdentifierRepository>()

    private val service =
        ManualMovieWatchCreateFlowService(
            manualMovieCatalogService = manualMovieCatalogService,
            manualMovieWatchRegistrationService = manualMovieWatchRegistrationService,
            externalIdentifierRepository = externalIdentifierRepository,
        )

    @Test
    fun `monta visualizacao com filme e ids externos`() {
        val request =
            ManualMovieWatchCreateRequest(
                watchedAt = Instant.parse("2026-03-01T10:00:00Z"),
                title = "Dune",
                year = 2021,
                imdbId = "tt1160419",
            )

        val movie = Movie(id = 42, originalTitle = "Dune", year = 2021, coverUrl = "/img.jpg", fingerprint = "fp")

        every { manualMovieCatalogService.resolveOrCreate(request) } returns
            ManualMovieCatalogService.MovieCatalogResult(
                movie = movie,
                created = false,
                coverAssigned = false,
            )
        every { manualMovieWatchRegistrationService.register(42, request.watchedAt) } returns false
        every { externalIdentifierRepository.findByEntityTypeAndEntityId(EntityType.MOVIE, 42) } returns
            listOf(
                ExternalIdentifier(entityType = EntityType.MOVIE, entityId = 42, provider = Provider.IMDB, externalId = "tt1160419"),
            )

        val response = service.execute(request)

        assertEquals(42, response.movieId)
        assertEquals("Dune", response.title)
        assertEquals("MANUAL", response.source)
        assertFalse(response.watchInserted)
        assertEquals(1, response.externalIds.size)
        assertEquals("IMDB", response.externalIds.first().provider)
    }
}
