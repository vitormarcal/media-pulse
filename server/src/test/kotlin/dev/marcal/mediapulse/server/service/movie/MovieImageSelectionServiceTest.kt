package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieImageSelectionRequest
import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.integration.tmdb.TmdbMovieImageResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbMovieImagesResponse
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.Optional

class MovieImageSelectionServiceTest {
    private val movies = mockk<MovieRepository>()
    private val images = mockk<MovieImageCrudRepository>(relaxUnitFun = true)
    private val tmdb = mockk<TmdbApiClient>()
    private val storage = mockk<LibraryImageStorageService>()
    private val library = mockk<VisualLibraryClient>()
    private val assignment = MovieImageAssignmentService(movies, images)
    private val service = MovieImageSelectionService(movies, images, tmdb, TmdbProperties(enabled = true), storage, assignment, library)
    private val film = Movie(id = 7, originalTitle = "Movie", coverUrl = "current", tmdbId = "42", fingerprint = "movie")
    private val poster = TmdbMovieImageResponse("/poster.jpg", 780, 1170)
    private val backdrop = TmdbMovieImageResponse("/backdrop.jpg", 1920, 1080)

    init {
        every { movies.findById(7) } returns Optional.of(film)
        every { tmdb.fetchMovieImages("42") } returns
            TmdbMovieImagesResponse(listOf(poster, poster, TmdbMovieImageResponse("//foreign.example/image.jpg")), listOf(backdrop))
    }

    @Test
    fun `preview returns distinct safe provider images without storing or downloading`() {
        val options = service.candidates(7).images
        assertEquals(listOf("POSTER", "BACKDROP"), options.map { it.kind })
        assertEquals("https://image.tmdb.org/t/p/original/poster.jpg", options.first().url)
        assertEquals("https://image.tmdb.org/t/p/w780/poster.jpg", options.first().previewUrl)
        verify { storage wasNot Called }
        verify { images wasNot Called }
    }

    @Test
    fun `partial save inserts only successful references without touching cover or primary`() {
        val (first, second) = service.candidates(7).images
        val unknown = "0".repeat(64)
        val content = ImageContent(byteArrayOf(1), null)
        every { storage.downloadProviderImage(first.url) } returns content
        every { storage.downloadProviderImage(second.url) } throws IllegalStateException()
        every { storage.saveMovie(content, 7, "Movie", "TMDB", first.id.take(24)) } returns "stored"
        every { images.lockMovieRowForPrimaryUpdate(7) } returns 7

        val result = service.save(7, MovieImageSelectionRequest(listOf(first.id, second.id, unknown, first.id)))

        assertEquals(listOf(first.id), result.savedIds)
        assertEquals(listOf(second.id, unknown), result.failedIds)
        verify(exactly = 1) { storage.downloadProviderImage(first.url) }
        verifyOrder {
            images.lockMovieRowForPrimaryUpdate(7)
            images.insertIgnore(7, "stored", false)
        }
        verify(exactly = 0) {
            movies.updateCover(any(), any())
            images.clearPrimaryForMovie(any())
            images.markPrimaryForMovie(any(), any())
        }
    }

    @Test
    fun `invalid or oversized selection is rejected before provider calls`() {
        for (ids in listOf(emptyList(), listOf(null), listOf("https://example.test/image.jpg"), List(51) { "a".repeat(64) })) {
            val error = assertThrows<ResponseStatusException> { service.save(7, MovieImageSelectionRequest(ids)) }
            assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
        }
        verify { tmdb wasNot Called }
        verify { storage wasNot Called }
    }

    @Test
    fun `unlinked movie gives guidance and does not query provider`() {
        every { movies.findById(7) } returns Optional.of(film.copy(tmdbId = null))
        val result = service.candidates(7)
        assertTrue(result.images.isEmpty())
        assertTrue(result.warnings.single().contains("Atualizar dados"))
        verify { tmdb wasNot Called }
    }

    @Test
    fun `unavailable library skips downloads without blocking selection`() {
        every { library.enabled } returns false
        val response = service.lookup(7, MovieImageSelectionRequest(listOf("a".repeat(64))))
        assertFalse(response.available)
        assertEquals("UNAVAILABLE", response.results.single().state)
        verify { storage wasNot Called }
        verify { tmdb wasNot Called }
    }
}
