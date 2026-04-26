package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MovieExternalIdDto
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCompaniesCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCompanyAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCompanyRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import kotlin.test.assertEquals

class MovieCompaniesServiceTest {
    private val movieRepository = mockk<MovieRepository>()
    private val movieQueryRepository = mockk<MovieQueryRepository>()
    private val movieCompanyRepository = mockk<MovieCompanyRepository>(relaxed = true)
    private val movieCompanyAssignmentRepository = mockk<MovieCompanyAssignmentRepository>(relaxed = true)
    private val movieCompaniesCrudRepository = mockk<MovieCompaniesCrudRepository>()
    private val tmdbApiClient = mockk<TmdbApiClient>()
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>()
    private val transactionTemplate = mockk<TransactionTemplate>()

    private val service =
        MovieCompaniesService(
            movieRepository = movieRepository,
            movieQueryRepository = movieQueryRepository,
            movieCompanyRepository = movieCompanyRepository,
            movieCompanyAssignmentRepository = movieCompanyAssignmentRepository,
            movieCompaniesCrudRepository = movieCompaniesCrudRepository,
            tmdbApiClient = tmdbApiClient,
            manualMovieCatalogService = manualMovieCatalogService,
            transactionTemplate = transactionTemplate,
        )

    @Test
    fun `sync should replace movie companies from tmdb`() {
        val movie = Movie(id = 7, originalTitle = "Spirited Away", fingerprint = "fp7")
        val capturedCompanies = slot<List<MovieCompanyAssignmentRepository.UpsertMovieCompanyRequest>>()

        every { movieRepository.findById(7) } returns Optional.of(movie)
        every { movieQueryRepository.getMovieDetails(7) } returns movieDetails(7, "129")
        every { tmdbApiClient.fetchMovieDetails("129") } returns
            TmdbApiClient.TmdbMovieDetails(
                title = "Spirited Away",
                originalTitle = "Sen to Chihiro no kamikakushi",
                imdbId = null,
                overview = null,
                releaseYear = 2001,
                posterPath = null,
                backdropPath = null,
                productionCompanies =
                    listOf(
                        TmdbApiClient.TmdbMovieCompany(
                            tmdbId = "10342",
                            name = "Studio Ghibli",
                            logoPath = "/ghibli.png",
                            originCountry = "JP",
                        ),
                    ),
            )
        every { manualMovieCatalogService.buildTmdbImageUrl("/ghibli.png") } returns "https://image.tmdb.org/t/p/w780/ghibli.png"
        every { movieCompanyRepository.findByTmdbId("10342") } returns null
        every { movieCompanyRepository.save(any()) } answers { firstArg() }
        every { movieCompanyAssignmentRepository.replaceForMovie(7, capture(capturedCompanies)) } returns Unit
        every { movieCompaniesCrudRepository.markCompaniesSynced(7) } returns 1
        every { movieQueryRepository.getMovieCompanies(7) } returns emptyList()

        val response = service.syncFromTmdb(7)

        assertEquals(1, response.syncedCount)
        assertEquals(1, capturedCompanies.captured.size)
        verify(exactly = 1) { movieCompaniesCrudRepository.markCompaniesSynced(7) }
    }

    private fun movieDetails(
        movieId: Long,
        tmdbId: String,
    ) = MovieDetailsResponse(
        movieId = movieId,
        title = "Movie $movieId",
        originalTitle = "Movie $movieId",
        description = null,
        year = null,
        coverUrl = null,
        images = emptyList(),
        watches = emptyList(),
        externalIds = listOf(MovieExternalIdDto(provider = "TMDB", externalId = tmdbId)),
    )
}
