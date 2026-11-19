package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionRequest
import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.integration.tmdb.TmdbMovieImageResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbMovieImagesResponse
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.ShowSeasonCoverRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import dev.marcal.mediapulse.server.service.image.TmdbImageSelectionService
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyOrder
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotEquals

class ShowSeasonCoverServiceTest {
    private val shows = mockk<TvShowRepository>()
    private val episodes = mockk<TvEpisodeRepository>()
    private val covers = mockk<ShowSeasonCoverRepository>(relaxUnitFun = true)
    private val tmdb = mockk<TmdbApiClient>()
    private val storage = mockk<LibraryImageStorageService>()
    private val library = mockk<VisualLibraryClient>()
    private val service =
        ShowSeasonCoverService(shows, episodes, covers, TmdbImageSelectionService(tmdb, TmdbProperties(), storage, library))

    init {
        every { shows.findById(7) } returns Optional.of(TvShow(id = 7, originalTitle = "Series", fingerprint = "s", tmdbId = "42"))
        every { episodes.findByShowIdAndSeasonNumberOrderByEpisodeNumberAscIdAsc(7, any()) } returns
            listOf(TvEpisode(id = 1, showId = 7, title = "Episode", fingerprint = "e"))
        every { tmdb.fetchShowSeasonImages("42", any()) } returns
            TmdbMovieImagesResponse(listOf(TmdbMovieImageResponse("/poster.jpg")), listOf(TmdbMovieImageResponse("/backdrop.jpg")))
    }

    @Test
    fun `candidates contain season posters only with distinct identities per season including specials`() {
        val first = service.candidates(7, 0).images.single()
        val second = service.candidates(7, 1).images.single()
        assertEquals("POSTER", first.kind)
        assertNotEquals(first.id, second.id)
        verify(exactly = 0) { storage.downloadProviderImage(any()) }
    }

    @Test
    fun `stored image replaces only season link after upload succeeds and failure preserves old link`() {
        val candidate = service.candidates(7, 1).images.single()
        val content = ImageContent(byteArrayOf(1), null)
        every { storage.downloadProviderImage(candidate.url) } returns content
        every { storage.saveSeason(content, 7, 1, "Series", any()) } returns "stored"
        assertEquals(listOf(candidate.id), service.save(7, 1, TmdbImageSelectionRequest(listOf(candidate.id))).savedIds)
        verifyOrder {
            storage.saveSeason(content, 7, 1, "Series", any())
            covers.save(7, 1, "stored")
        }
        every { storage.saveSeason(content, 7, 1, "Series", any()) } throws IllegalStateException("offline")
        assertEquals(listOf(candidate.id), service.save(7, 1, TmdbImageSelectionRequest(listOf(candidate.id))).failedIds)
        verify(exactly = 1) { covers.save(any(), any(), any()) }
    }

    @Test
    fun `invalid foreign season selection and multiple covers cannot change stored cover`() {
        val other =
            service
                .candidates(7, 0)
                .images
                .single()
                .id
        assertEquals(listOf(other), service.save(7, 1, TmdbImageSelectionRequest(listOf(other))).failedIds)
        assertFailsWith<ResponseStatusException> { service.save(7, 1, TmdbImageSelectionRequest(listOf(other, other))) }
        assertFailsWith<ResponseStatusException> { service.candidates(7, -1) }
        every { episodes.findByShowIdAndSeasonNumberOrderByEpisodeNumberAscIdAsc(7, 2) } returns emptyList()
        assertFailsWith<ResponseStatusException> { service.candidates(7, 2) }
        verify(exactly = 0) { covers.save(any(), any(), any()) }
    }

    @Test
    fun `removal deletes season association only`() {
        service.remove(7, 0)
        verify { covers.remove(7, 0) }
        verify(exactly = 0) { storage.downloadProviderImage(any()) }
    }
}
