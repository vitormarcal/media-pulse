package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.movie.MoviePerson
import dev.marcal.mediapulse.server.repository.crud.MovieCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCreditsCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MoviePersonRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals

class MoviePersonFilmographyServiceTest {
    private val moviePersonRepository = mockk<MoviePersonRepository>()
    private val movieCreditAssignmentRepository = mockk<MovieCreditAssignmentRepository>(relaxed = true)
    private val movieCreditsCrudRepository = mockk<MovieCreditsCrudRepository>()
    private val tmdbApiClient = mockk<TmdbApiClient>()
    private val manualMovieCatalogService = mockk<ManualMovieCatalogService>()

    private val service =
        MoviePersonFilmographyService(
            moviePersonRepository = moviePersonRepository,
            movieCreditAssignmentRepository = movieCreditAssignmentRepository,
            movieCreditsCrudRepository = movieCreditsCrudRepository,
            tmdbApiClient = tmdbApiClient,
            manualMovieCatalogService = manualMovieCatalogService,
        )

    @Test
    fun `fetch filmography should reconcile local movie credits before returning`() {
        val person =
            MoviePerson(
                id = 44,
                tmdbId = "138",
                name = "Quentin Tarantino",
                normalizedName = "quentin tarantino",
                slug = "quentin-tarantino-138",
                profileUrl = null,
            )

        every { moviePersonRepository.findById(44) } returns Optional.of(person)
        every { tmdbApiClient.fetchPersonMovieCredits("138") } returns
            TmdbApiClient.TmdbPersonMovieCredits(
                cast =
                    listOf(
                        TmdbApiClient.TmdbPersonMovieCastCredit(
                            tmdbId = "101",
                            title = "Movie A",
                            originalTitle = "Movie A",
                            overview = null,
                            releaseYear = 2001,
                            posterPath = null,
                            backdropPath = null,
                            character = "Himself",
                            order = 12,
                        ),
                    ),
                crew =
                    listOf(
                        TmdbApiClient.TmdbPersonMovieCrewCredit(
                            tmdbId = "202",
                            title = "Movie B",
                            originalTitle = "Movie B",
                            overview = null,
                            releaseYear = 2004,
                            posterPath = null,
                            backdropPath = null,
                            department = "Directing",
                            job = "Director",
                        ),
                    ),
            )
        every { movieCreditsCrudRepository.findLocalMoviesByTmdbIds(listOf("202", "101")) } returns
            mapOf(
                "101" to MovieCreditsCrudRepository.LocalMovieByTmdbId(tmdbId = "101", movieId = 9, slug = "movie-a"),
                "202" to MovieCreditsCrudRepository.LocalMovieByTmdbId(tmdbId = "202", movieId = 11, slug = "movie-b"),
            )

        val response = service.fetchFilmography(44)

        assertEquals(2, response.members.size)
        verify(exactly = 2) { movieCreditAssignmentRepository.upsert(any()) }
        verify {
            movieCreditAssignmentRepository.upsert(
                match {
                    it.movieId == 9L &&
                        it.personId == 44L &&
                        it.characterName == "Himself"
                },
            )
        }
        verify {
            movieCreditAssignmentRepository.upsert(
                match {
                    it.movieId == 11L &&
                        it.personId == 44L &&
                        it.job == "Director"
                },
            )
        }
    }
}
