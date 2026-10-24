package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.api.movies.PersonFilmographyResponse
import dev.marcal.mediapulse.server.api.movies.PersonFilmographySyncState
import dev.marcal.mediapulse.server.api.movies.PersonShowFilmographyResponse
import dev.marcal.mediapulse.server.controller.people.PeopleController
import dev.marcal.mediapulse.server.service.movie.MovieCreditsService
import dev.marcal.mediapulse.server.service.person.PersonDetailsService
import dev.marcal.mediapulse.server.service.person.PersonFavoritesService
import dev.marcal.mediapulse.server.service.person.PersonFilmographyService
import dev.marcal.mediapulse.server.service.person.PersonHistoryService
import dev.marcal.mediapulse.server.service.person.PersonShowFilmographyService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class PeopleFilmographyHttpContractTest {
    @Test
    fun `get reads locally and post ensures each media independently with explicit availability`() {
        val movies = mockk<PersonFilmographyService>()
        val shows = mockk<PersonShowFilmographyService>()
        val mvc =
            MockMvcBuilders
                .standaloneSetup(
                    PeopleController(
                        mockk<PersonDetailsService>(),
                        mockk<MovieCreditsService>(),
                        movies,
                        shows,
                        mockk<PersonFavoritesService>(),
                        mockk<PersonHistoryService>(),
                    ),
                ).build()
        val movieResponse = PersonFilmographyResponse(44, "138", "Person", null, emptyList(), true, PersonFilmographySyncState.COMPACTED)
        val showResponse = PersonShowFilmographyResponse(44, "138", "Person", null, emptyList(), false, PersonFilmographySyncState.PENDING)
        every { movies.getFilmography(44) } returns movieResponse
        every { shows.getFilmography(44) } returns showResponse
        every { movies.ensureFilmography(44) } returns
            movieResponse.copy(compacted = false, syncState = PersonFilmographySyncState.COMPLETE)
        every { shows.ensureFilmography(44) } returns showResponse.copy(syncState = PersonFilmographySyncState.FAILED)

        mvc.perform(get("/api/people/44/filmography")).andExpect(status().isOk).andExpect(jsonPath("$.syncState").value("COMPACTED"))
        mvc.perform(get("/api/people/44/show-filmography")).andExpect(status().isOk).andExpect(jsonPath("$.syncState").value("PENDING"))
        verify(exactly = 0) { movies.ensureFilmography(any()) }
        verify(exactly = 0) { shows.ensureFilmography(any()) }
        mvc.perform(post("/api/people/44/filmography/ensure")).andExpect(status().isOk).andExpect(jsonPath("$.syncState").value("COMPLETE"))
        mvc
            .perform(
                post("/api/people/44/show-filmography/ensure"),
            ).andExpect(status().isOk)
            .andExpect(jsonPath("$.syncState").value("FAILED"))
        verify(exactly = 1) { movies.getFilmography(44) }
        verify(exactly = 1) { shows.getFilmography(44) }
        verify(exactly = 1) { movies.ensureFilmography(44) }
        verify(exactly = 1) { shows.ensureFilmography(44) }
    }
}
