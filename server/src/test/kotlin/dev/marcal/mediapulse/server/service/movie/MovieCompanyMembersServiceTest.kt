package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.movie.MovieCompany
import dev.marcal.mediapulse.server.repository.crud.MovieCompaniesCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCompanyAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCompanyRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals

class MovieCompanyMembersServiceTest {
    private val movieCompanyRepository = mockk<MovieCompanyRepository>()
    private val movieCompaniesCrudRepository = mockk<MovieCompaniesCrudRepository>(relaxed = true)
    private val movieCompanyAssignmentRepository = mockk<MovieCompanyAssignmentRepository>(relaxed = true)
    private val tmdbApiClient = mockk<TmdbApiClient>()
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>()

    private val service =
        MovieCompanyMembersService(
            movieCompanyRepository = movieCompanyRepository,
            movieCompaniesCrudRepository = movieCompaniesCrudRepository,
            movieCompanyAssignmentRepository = movieCompanyAssignmentRepository,
            tmdbApiClient = tmdbApiClient,
            manualMovieCatalogService = manualMovieCatalogService,
        )

    @Test
    fun `fetch members should reconcile local movie links and expose tmdb members`() {
        val company =
            MovieCompany(
                id = 12,
                tmdbId = "10342",
                name = "Studio Ghibli",
                normalizedName = "studio ghibli",
                slug = "studio-ghibli-10342",
                logoUrl = null,
                originCountry = "JP",
            )

        every { movieCompanyRepository.findById(12) } returns Optional.of(company)
        every { tmdbApiClient.fetchCompanyMovies("10342") } returns
            TmdbApiClient.TmdbCompanyMovies(
                companyTmdbId = "10342",
                movies =
                    listOf(
                        TmdbApiClient.TmdbMovieSearchItem(
                            tmdbId = "129",
                            title = "Spirited Away",
                            originalTitle = "Sen to Chihiro no kamikakushi",
                            overview = null,
                            releaseYear = 2001,
                            posterPath = "/poster.jpg",
                        ),
                    ),
            )
        every { movieCompaniesCrudRepository.findLocalMoviesByTmdbIds(listOf("129")) } returns
            mapOf(
                "129" to
                    MovieCompaniesCrudRepository.LocalMovieByTmdbId(
                        tmdbId = "129",
                        movieId = 7,
                        slug = "spirited-away",
                    ),
            )
        every { manualMovieCatalogService.buildTmdbImageUrl("/poster.jpg") } returns "https://image.tmdb.org/t/p/w780/poster.jpg"

        val response = service.fetchMembers(12)

        assertEquals(1, response.members.size)
        assertEquals(true, response.members.first().inCatalog)
        verify(exactly = 1) {
            movieCompanyAssignmentRepository.upsert(
                withArg {
                    assertEquals(7, it.movieId)
                    assertEquals(12, it.companyId)
                },
            )
        }
    }
}
