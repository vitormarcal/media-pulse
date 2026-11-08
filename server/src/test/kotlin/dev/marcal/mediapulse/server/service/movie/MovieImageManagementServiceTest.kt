package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieImage
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MovieImageManagementServiceTest {
    private val movies = mockk<MovieRepository>(relaxed = true)
    private val images = mockk<MovieImageCrudRepository>(relaxed = true)
    private val service = MovieImageManagementService(movies, images)
    private val game = Movie(id = 7, originalTitle = "Filme", fingerprint = "game", coverUrl = "old")
    private val gallery = listOf(MovieImage(1, 7, "old", isPrimary = true), MovieImage(3, 7, "new"), MovieImage(2, 7, "other"))

    init {
        every { images.lockMovieRowForPrimaryUpdate(7) } returns 7
        every { movies.findById(7) } returns Optional.of(game)
        every { images.findByMovieIdOrderByIsPrimaryDescIdDesc(7) } returns gallery
    }

    @Test
    fun `existing image becomes canonical cover and unique primary without deleting links`() {
        assertEquals("new", service.update(7, "new", false).coverUrl)
        verify {
            images.clearPrimaryForMovie(7)
            images.markPrimaryForMovie(7, "new")
            movies.updateCover(7, "new")
        }
        verify(exactly = 0) { images.removeForMovie(any(), any()) }
    }

    @Test
    fun `removing cover chooses first remaining in gallery order`() {
        assertEquals("new", service.update(7, "old", true).coverUrl)
        verify {
            images.removeForMovie(7, "old")
            images.markPrimaryForMovie(7, "new")
            movies.updateCover(7, "new")
        }
    }

    @Test
    fun `removing another image preserves cover`() {
        assertEquals("old", service.update(7, "new", true).coverUrl)
        verify { movies.updateCover(7, "old") }
    }

    @Test
    fun `removing final image clears canonical cover and primary`() {
        every { images.findByMovieIdOrderByIsPrimaryDescIdDesc(7) } returns gallery.take(1)
        assertEquals(null, service.update(7, "old", true).coverUrl)
        verify { movies.updateCover(7, null) }
        verify(exactly = 0) { images.markPrimaryForMovie(any(), any()) }
    }

    @Test
    fun `legacy cover without association can be removed or made primary`() {
        every { images.findByMovieIdOrderByIsPrimaryDescIdDesc(7) } returns emptyList()
        assertEquals("old", service.update(7, "old", false).coverUrl)
        verify { images.insertIgnore(7, "old", false) }
        assertEquals(null, service.update(7, "old", true).coverUrl)
    }

    @Test
    fun `missing game and unlinked images never change data`() {
        assertFailsWith<ResponseStatusException> { service.update(7, "unlinked", false) }
        assertFailsWith<ResponseStatusException> { service.update(7, "unlinked", true) }
        every { images.lockMovieRowForPrimaryUpdate(8) } returns null
        assertFailsWith<ResponseStatusException> { service.update(8, "old", true) }
        verify(exactly = 0) {
            images.removeForMovie(any(), any())
            images.clearPrimaryForMovie(any())
            movies.updateCover(any(), any())
        }
    }
}
