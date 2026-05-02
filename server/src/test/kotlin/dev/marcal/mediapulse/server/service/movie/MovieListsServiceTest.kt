package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieListAttachRequest
import dev.marcal.mediapulse.server.api.movies.MovieListCoverUpdateRequest
import dev.marcal.mediapulse.server.api.movies.MovieListCreateRequest
import dev.marcal.mediapulse.server.api.movies.MovieListOrderUpdateRequest
import dev.marcal.mediapulse.server.api.movies.MovieListSummaryDto
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieList
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.crud.MovieListItemCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieListRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals

class MovieListsServiceTest {
    private val movieRepository = mockk<MovieRepository>()
    private val movieListRepository = mockk<MovieListRepository>(relaxed = true)
    private val movieListItemCrudRepository = mockk<MovieListItemCrudRepository>(relaxed = true)
    private val movieQueryRepository = mockk<MovieQueryRepository>()

    private val service =
        MovieListsService(
            movieRepository = movieRepository,
            movieListRepository = movieListRepository,
            movieListItemCrudRepository = movieListItemCrudRepository,
            movieQueryRepository = movieQueryRepository,
        )

    @Test
    fun `create should persist list and return summary`() {
        every { movieListRepository.findByNormalizedName("favoritos") } returns null
        every { movieListRepository.save(any()) } answers { firstArg<MovieList>().copy(id = 7) }
        every { movieQueryRepository.getMovieListSummary(7) } returns
            MovieListSummaryDto(
                listId = 7,
                name = "Favoritos",
                slug = "favoritos",
                description = "Filmes que sempre voltam.",
                itemCount = 0,
            )

        val response = service.create(MovieListCreateRequest(name = "Favoritos", description = "Filmes que sempre voltam."))

        assertEquals(7, response.listId)
    }

    @Test
    fun `attach movie should upsert list item and return summary`() {
        every { movieRepository.findById(5) } returns Optional.of(Movie(id = 5, originalTitle = "Movie", fingerprint = "fp"))
        every { movieListRepository.findById(2) } returns
            Optional.of(MovieList(id = 2, name = "Oscar 2025", normalizedName = "oscar 2025", slug = "oscar-2025"))
        every { movieListRepository.save(any()) } answers { firstArg<MovieList>() }
        every { movieQueryRepository.getMovieListSummary(2) } returns
            MovieListSummaryDto(
                listId = 2,
                name = "Oscar 2025",
                slug = "oscar-2025",
                description = null,
                itemCount = 4,
            )

        val response = service.attachMovie(movieId = 5, request = MovieListAttachRequest(listId = 2))

        assertEquals(2, response.listId)
        verify(exactly = 1) { movieListItemCrudRepository.upsertItem(2, 5) }
        verify(exactly = 1) { movieListRepository.save(any()) }
    }

    @Test
    fun `create should reuse existing list with same normalized name`() {
        every {
            movieListRepository.findByNormalizedName("favoritos")
        } returns MovieList(id = 9, name = "Favoritos", normalizedName = "favoritos", slug = "favoritos")
        every { movieQueryRepository.getMovieListSummary(9) } returns
            MovieListSummaryDto(
                listId = 9,
                name = "Favoritos",
                slug = "favoritos",
                description = null,
                itemCount = 3,
            )

        val response = service.create(MovieListCreateRequest(name = " Favoritos ", description = null))

        assertEquals(9, response.listId)
        verify(exactly = 0) { movieListRepository.save(any()) }
    }

    @Test
    fun `update order should rewrite persisted positions`() {
        every { movieListRepository.findById(2) } returns
            Optional.of(MovieList(id = 2, name = "Oscar 2025", normalizedName = "oscar 2025", slug = "oscar-2025"))
        every { movieListItemCrudRepository.listPositions(2) } returns
            listOf(
                MovieListItemCrudRepository.MovieListPositionRecord(movieId = 10, position = 1),
                MovieListItemCrudRepository.MovieListPositionRecord(movieId = 11, position = 2),
                MovieListItemCrudRepository.MovieListPositionRecord(movieId = 12, position = 3),
            )
        every { movieListRepository.save(any()) } answers { firstArg<MovieList>() }

        service.updateOrder(
            listId = 2,
            request = MovieListOrderUpdateRequest(movieIds = listOf(12, 10, 11)),
        )

        verify(exactly = 1) { movieListItemCrudRepository.updatePosition(2, 12, 1) }
        verify(exactly = 1) { movieListItemCrudRepository.updatePosition(2, 10, 2) }
        verify(exactly = 1) { movieListItemCrudRepository.updatePosition(2, 11, 3) }
        verify(exactly = 1) { movieListRepository.save(any()) }
    }

    @Test
    fun `update cover should persist selected movie from list`() {
        every { movieListRepository.findById(2) } returns
            Optional.of(MovieList(id = 2, name = "Oscar 2025", normalizedName = "oscar 2025", slug = "oscar-2025"))
        every { movieListItemCrudRepository.listPositions(2) } returns
            listOf(
                MovieListItemCrudRepository.MovieListPositionRecord(movieId = 10, position = 1),
                MovieListItemCrudRepository.MovieListPositionRecord(movieId = 11, position = 2),
            )
        every { movieListRepository.save(any()) } answers { firstArg<MovieList>() }
        every { movieQueryRepository.getMovieListSummary(2) } returns
            MovieListSummaryDto(
                listId = 2,
                name = "Oscar 2025",
                slug = "oscar-2025",
                description = null,
                itemCount = 2,
                coverMovieId = 11,
            )

        val response = service.updateCover(listId = 2, request = MovieListCoverUpdateRequest(coverMovieId = 11))

        assertEquals(11, response.coverMovieId)
        verify(exactly = 1) { movieListRepository.save(match { it.coverMovieId == 11L }) }
    }
}
