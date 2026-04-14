package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.ManualMovieCatalogCreateRequest
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ManualMovieCatalogCreateFlowServiceTest {
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>()
    private val externalIdentifierRepository = mockk<ExternalIdentifierRepository>()

    private val service =
        ManualMovieCatalogCreateFlowService(
            manualMovieCatalogService = manualMovieCatalogService,
            externalIdentifierRepository = externalIdentifierRepository,
        )

    @Test
    fun `monta resposta de catalogo com filme e ids externos`() {
        val request = ManualMovieCatalogCreateRequest(title = "Dune", year = 2021, tmdbId = "438631")
        val movie = Movie(id = 42, originalTitle = "Dune", year = 2021, slug = "dune", coverUrl = "/img.jpg", fingerprint = "fp")

        every {
            manualMovieCatalogService.resolveOrCreate(
                ManualMovieCatalogService.MovieCatalogUpsertRequest(
                    title = "Dune",
                    year = 2021,
                    tmdbId = "438631",
                    imdbId = null,
                ),
            )
        } returns ManualMovieCatalogService.MovieCatalogResult(movie = movie, created = true, coverAssigned = false)
        every { externalIdentifierRepository.findByEntityTypeAndEntityId(EntityType.MOVIE, 42) } returns
            listOf(ExternalIdentifier(entityType = EntityType.MOVIE, entityId = 42, provider = Provider.TMDB, externalId = "438631"))

        val response = service.execute(request)

        assertEquals(42, response.movieId)
        assertEquals("dune", response.slug)
        assertTrue(response.createdMovie)
        assertEquals("TMDB", response.externalIds.single().provider)
    }
}
