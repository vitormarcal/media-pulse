package dev.marcal.mediapulse.server.controller.admin

import dev.marcal.mediapulse.server.api.movies.MovieTermsBatchSyncResponse
import dev.marcal.mediapulse.server.api.movies.PersonFilmographyResponse
import dev.marcal.mediapulse.server.api.shows.ShowCreditsBatchSyncResponse
import dev.marcal.mediapulse.server.service.movie.MovieCollectionBackfillService
import dev.marcal.mediapulse.server.service.movie.MovieCollectionMembersService
import dev.marcal.mediapulse.server.service.movie.MovieCompaniesService
import dev.marcal.mediapulse.server.service.movie.MovieCompanyMembersService
import dev.marcal.mediapulse.server.service.movie.MovieCreditsService
import dev.marcal.mediapulse.server.service.movie.MovieTermsService
import dev.marcal.mediapulse.server.service.person.PersonFilmographyService
import dev.marcal.mediapulse.server.service.person.PersonShowFilmographyService
import dev.marcal.mediapulse.server.service.tv.ShowCreditsService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class OperationalAdminControllerTest {
    @Test
    fun `movie batch repair should preserve limit normalization`() {
        val terms = mockk<MovieTermsService>()
        val controller =
            MovieAdminController(
                terms,
                mockk<MovieCompaniesService>(),
                mockk<MovieCreditsService>(),
                mockk<MovieCollectionBackfillService>(),
                mockk<MovieCollectionMembersService>(),
                mockk<MovieCompanyMembersService>(),
            )
        every { terms.syncAllFromTmdb(1000) } returns
            MovieTermsBatchSyncResponse(1000, 10, 10, 9, 1)

        val response = controller.syncAllTerms(5000)

        assertEquals(9, response.synced)
        verify { terms.syncAllFromTmdb(1000) }
    }

    @Test
    fun `show batch repair should preserve limit normalization`() {
        val credits = mockk<ShowCreditsService>()
        val controller = ShowAdminController(credits)
        every { credits.syncAllFromTmdb(1000) } returns
            ShowCreditsBatchSyncResponse(1000, 10, 10, 8, 2)

        val response = controller.syncAllCredits(5000)

        assertEquals(8, response.synced)
        verify { credits.syncAllFromTmdb(1000) }
    }

    @Test
    fun `people repair should delegate to filmography service`() {
        val movies = mockk<PersonFilmographyService>()
        val controller = PeopleAdminController(movies, mockk<PersonShowFilmographyService>())
        every { movies.refreshAndGetFilmography(44) } returns
            PersonFilmographyResponse(44, "138", "Quentin Tarantino", null, emptyList())

        val response = controller.refreshMovieFilmography(44)

        assertEquals("138", response.tmdbId)
        verify { movies.refreshAndGetFilmography(44) }
    }
}
