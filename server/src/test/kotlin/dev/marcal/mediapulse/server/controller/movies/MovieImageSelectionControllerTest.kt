package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.MovieImageCandidatesResponse
import dev.marcal.mediapulse.server.api.movies.MovieImageLookupResponse
import dev.marcal.mediapulse.server.api.movies.MovieImageSelectionRequest
import dev.marcal.mediapulse.server.api.movies.MovieImageSelectionResponse
import dev.marcal.mediapulse.server.config.OwnerAccessFilter
import dev.marcal.mediapulse.server.config.OwnerAuthProperties
import dev.marcal.mediapulse.server.service.auth.OwnerAuthService
import dev.marcal.mediapulse.server.service.movie.MovieImageSelectionService
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
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
    private val auth = mockk<OwnerAuthService> { every { generation() } returns 1L }
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(MovieImageSelectionController(service))
            .addFilters<org.springframework.test.web.servlet.setup.StandaloneMockMvcBuilder>(OwnerAccessFilter(auth, OwnerAuthProperties()))
            .build()
    private val session =
        MockHttpSession().apply {
            setAttribute("ownerGeneration", 1L)
            setAttribute("csrf", "test-token")
        }

    @Test
    fun `candidate lookup requires owner without requiring csrf for read`() {
        val path = "/api/movies/7/images/candidates"
        every { service.candidates(7) } returns MovieImageCandidatesResponse(emptyList())
        for (unauthorized in listOf(path, "/api/movies/+7/images/candidates", "$path;variant=1")) {
            mvc.perform(get(unauthorized).servletPath(unauthorized)).andExpect(status().isUnauthorized)
        }
        mvc
            .perform(get(path).servletPath(path).session(session))
            .andExpect(status().isOk)
            .andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(jsonPath("$.images").isArray)
    }

    @Test
    fun `save and library lookup require owner and csrf`() {
        val id = "a".repeat(64)
        val body = MovieImageSelectionRequest(listOf(id))
        every { service.save(7, body) } returns MovieImageSelectionResponse(listOf(id), emptyList())
        every { service.lookup(7, body) } returns MovieImageLookupResponse(false, emptyList())
        for (action in listOf("selection", "lookup")) {
            val path = "/api/movies/7/images/$action"

            fun request() = post(path).servletPath(path).contentType(MediaType.APPLICATION_JSON).content("""{"ids":["$id"]}""")
            mvc.perform(request()).andExpect(status().isUnauthorized)
            mvc.perform(request().session(session)).andExpect(status().isForbidden)
            mvc.perform(request().session(session).header("X-CSRF-Token", "test-token")).andExpect(status().isOk)
        }
    }
}
