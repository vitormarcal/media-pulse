package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.image.TmdbImageCandidatesResponse
import dev.marcal.mediapulse.server.api.image.TmdbImageLookupResponse
import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionRequest
import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionResponse
import dev.marcal.mediapulse.server.config.OwnerAccessFilter
import dev.marcal.mediapulse.server.config.OwnerAuthProperties
import dev.marcal.mediapulse.server.controller.shows.ShowImageSelectionController
import dev.marcal.mediapulse.server.service.auth.OwnerAuthService
import dev.marcal.mediapulse.server.service.movie.MovieImageSelectionService
import dev.marcal.mediapulse.server.service.tv.ShowImageSelectionService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import org.springframework.http.MediaType
import org.springframework.mock.web.MockHttpSession
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.header
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class MovieImageSelectionControllerTest {
    private val service = mockk<MovieImageSelectionService>()
    private val shows = mockk<ShowImageSelectionService>()
    private val auth = mockk<OwnerAuthService> { every { generation() } returns 1L }
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(MovieImageSelectionController(service), ShowImageSelectionController(shows))
            .addFilters<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(OwnerAccessFilter(auth, OwnerAuthProperties()))
            .build()
    private val session =
        MockHttpSession().apply {
            setAttribute("ownerGeneration", 1L)
            setAttribute("csrf", "test-token")
        }

    @ParameterizedTest
    @ValueSource(strings = ["movies", "shows"])
    fun `candidate lookup requires owner without requiring csrf for read`(domain: String) {
        val path = "/api/$domain/7/images/candidates"
        every { service.candidates(7) } returns TmdbImageCandidatesResponse(emptyList())
        every { shows.candidates(7) } returns TmdbImageCandidatesResponse(emptyList())
        for (unauthorized in listOf(path, "/api/$domain/+7/images/candidates", "$path;variant=1")) {
            mvc.perform(get(unauthorized).servletPath(unauthorized)).andExpect(status().isUnauthorized)
        }
        mvc
            .perform(get(path).servletPath(path).session(session))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.images").isArray)
    }

    @ParameterizedTest
    @ValueSource(strings = ["movies", "shows"])
    fun `save and library lookup require owner and csrf`(domain: String) {
        val id = "a".repeat(64)
        val body = TmdbImageSelectionRequest(listOf(id))
        every { service.save(7, body) } returns TmdbImageSelectionResponse(listOf(id), emptyList())
        every { shows.save(7, body) } returns TmdbImageSelectionResponse(listOf(id), emptyList())
        every { service.lookup(7, body) } returns TmdbImageLookupResponse(false, emptyList())
        every { shows.lookup(7, body) } returns TmdbImageLookupResponse(false, emptyList())
        for (action in listOf("selection", "lookup")) {
            val path = "/api/$domain/7/images/$action"

            fun request() = post(path).servletPath(path).contentType(MediaType.APPLICATION_JSON).content("""{"ids":["$id"]}""")
            mvc.perform(request()).andExpect(status().isUnauthorized)
            mvc.perform(request().session(session)).andExpect(status().isForbidden)
            mvc.perform(request().session(session).header("X-CSRF-Token", "test-token")).andExpect(status().isOk)
        }
    }
}
