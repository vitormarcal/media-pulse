package dev.marcal.mediapulse.server.controller.books

import dev.marcal.mediapulse.server.api.books.BookGroupDetailsDto
import dev.marcal.mediapulse.server.api.books.BookGroupDto
import dev.marcal.mediapulse.server.model.book.BookGroupKind
import dev.marcal.mediapulse.server.repository.BookQueryRepository
import dev.marcal.mediapulse.server.service.books.BookSeriesService
import dev.marcal.mediapulse.server.service.books.BookSeriesSuggestionsService
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.request
import org.springframework.test.web.servlet.result.MockMvcResultMatchers.status
import org.springframework.test.web.servlet.setup.MockMvcBuilders

class BookSeriesControllerTest {
    private val service = mockk<BookSeriesService>()
    private val suggestions = mockk<BookSeriesSuggestionsService>()
    private val mvc =
        MockMvcBuilders
            .standaloneSetup(
                BooksController(mockk<BookQueryRepository>()),
                BookSeriesController(service, suggestions),
            ).build()

    @Test
    fun `series and universe routes take precedence over numeric book detail route`() {
        every { service.kind("series") } returns BookGroupKind.SERIES
        every { service.kind("universes") } returns BookGroupKind.UNIVERSES
        every { service.list(any()) } returns listOf(BookGroupDto(1, "Group", 0))
        every { service.details(any(), 1) } returns BookGroupDetailsDto(1, "Group", emptyList())
        every { service.create(BookGroupKind.SERIES, "New") } returns BookGroupDetailsDto(2, "New", emptyList())
        for (kind in listOf("series", "universes")) {
            mvc.perform(get("/api/books/$kind")).andExpect(status().isOk).andExpect(jsonPath("$[0].id").value(1))
            mvc.perform(get("/api/books/$kind/1")).andExpect(status().isOk).andExpect(jsonPath("$.items").isArray)
        }
        mvc
            .perform(post("/api/books/series").contentType(MediaType.APPLICATION_JSON).content("""{"name":"New"}"""))
            .andExpect(status().isCreated)
            .andExpect(jsonPath("$.id").value(2))
    }

    @Test
    fun `explicit suggestion request supports coroutine dispatch`() {
        coEvery { suggestions.preview(1) } returns emptyList()
        val result = mvc.perform(post("/api/books/1/series/suggestions")).andExpect(request().asyncStarted()).andReturn()
        mvc.perform(asyncDispatch(result)).andExpect(status().isOk).andExpect(jsonPath("$").isEmpty)
    }
}
