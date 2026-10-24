package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieCollectionSyncState
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.crud.MovieCollectionCrudRepository
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovieCollectionMembersServiceTest {
    private val repository = mockk<MovieCollectionCrudRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiClient>()
    private val catalog = mockk<ManualMovieCatalogService>()
    private val tx = mockk<TxUtil>()
    private val service = MovieCollectionMembersService(repository, tmdb, catalog, tx)

    init {
        every { tx.inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() }
    }

    @Test
    fun `should return collection members from local snapshot`() {
        every { repository.findCollection(12) } returns collection()
        every { repository.findMembers(12) } returns
            listOf(
                MovieCollectionCrudRepository.MovieCollectionMemberRecord(
                    tmdbId = "603",
                    title = "The Matrix",
                    originalTitle = "The Matrix",
                    year = 1999,
                    overview = "Reality bends.",
                    posterUrl = "/covers/matrix.jpg",
                    backdropUrl = null,
                    localMovieId = 10,
                    localSlug = "the-matrix",
                ),
                MovieCollectionCrudRepository.MovieCollectionMemberRecord(
                    tmdbId = "604",
                    title = "The Matrix Reloaded",
                    originalTitle = "The Matrix Reloaded",
                    year = 2003,
                    overview = "The story continues.",
                    posterUrl = "/covers/reloaded.jpg",
                    backdropUrl = null,
                    localMovieId = null,
                    localSlug = null,
                ),
            )

        val response = service.getMembers(12)

        assertEquals("Matrix films.", response.overview)
        assertEquals(2, response.members.size)
        assertTrue(response.members[0].inCatalog)
        assertFalse(response.members[1].inCatalog)
        verify(exactly = 0) { tmdb.fetchMovieCollectionDetails(any()) }
    }

    @Test
    fun `should persist provider response as an atomic snapshot`() {
        val members = slot<List<MovieCollectionCrudRepository.MovieCollectionMemberSnapshot>>()
        every { repository.findCollection(12) } returns collection()
        every { tmdb.fetchMovieCollectionDetails("2344") } returns tmdbCollection()
        every { catalog.buildTmdbImageUrl(any()) } answers { "https://image.tmdb.org${firstArg<String>()}" }
        every {
            repository.replaceMemberSnapshot(
                collectionId = 12,
                name = any(),
                overview = any(),
                posterUrl = any(),
                backdropUrl = any(),
                members = capture(members),
            )
        } returns Unit

        assertTrue(service.refreshMembers(12))

        assertEquals(listOf("603", "604"), members.captured.map { it.tmdbId })
        assertEquals("https://image.tmdb.org/matrix.jpg", members.captured.first().posterUrl)
    }

    @Test
    fun `should record failed provider attempt`() {
        every { repository.findCollection(12) } returns collection()
        every { tmdb.fetchMovieCollectionDetails("2344") } returns null

        assertFalse(service.refreshMembers(12))

        verify { repository.markMemberSyncFailure(12, "TMDb collection unavailable") }
        verify(exactly = 0) { repository.replaceMemberSnapshot(any(), any(), any(), any(), any(), any()) }
    }

    @Test
    fun `pending collection returns known local movies without fetching provider`() {
        every { repository.findCollection(12) } returns collection()
        every { repository.findMembers(12) } returns emptyList()
        every { repository.findLocalMembers(12) } returns
            listOf(
                MovieCollectionCrudRepository.MovieCollectionMemberRecord(
                    tmdbId = "603",
                    title = "The Matrix",
                    originalTitle = "The Matrix",
                    year = 1999,
                    overview = null,
                    posterUrl = null,
                    backdropUrl = null,
                    localMovieId = 10,
                    localSlug = "the-matrix",
                ),
            )

        val response = service.getMembers(12)

        assertEquals(MovieCollectionSyncState.PENDING, response.syncState)
        assertEquals(10L, response.members.single().localMovieId)
        verify(exactly = 0) { tmdb.fetchMovieCollectionDetails(any()) }
    }

    @Test
    fun `failed collection exposes failure instead of claiming it is empty`() {
        every { repository.findCollection(12) } returns collection().copy(membersSyncFailed = true)
        every { repository.findMembers(12) } returns emptyList()

        assertEquals(MovieCollectionSyncState.FAILED, service.getMembers(12).syncState)
    }

    @Test
    fun `completed empty collection does not fall back to local movies`() {
        every { repository.findCollection(12) } returns collection().copy(membersSynced = true)
        every { repository.findMembers(12) } returns emptyList()

        val response = service.getMembers(12)

        assertEquals(MovieCollectionSyncState.COMPLETE, response.syncState)
        assertTrue(response.members.isEmpty())
        verify(exactly = 0) { repository.findLocalMembers(any()) }
    }

    @Test
    fun `worker records unexpected failure and continues other collections`() {
        every { repository.findPendingCollectionIds(25) } returns listOf(12, 13)
        every { repository.findCollection(any()) } returns collection()
        every { tmdb.fetchMovieCollectionDetails("2344") } throws IllegalStateException("provider timeout")

        val result = service.enrichPending()

        assertEquals(2, result.pending)
        verify { repository.markMemberSyncFailure(12, "provider timeout") }
        verify { repository.markMemberSyncFailure(13, "provider timeout") }
    }

    @Test
    fun `explicit retry preserves not found response for unknown collection`() {
        every { repository.findCollection(99) } returns null

        val error = assertFailsWith<ResponseStatusException> { service.refreshAndGetMembers(99) }

        assertEquals(HttpStatus.NOT_FOUND, error.statusCode)
    }

    private fun collection() =
        MovieCollectionCrudRepository.MovieCollectionRecord(
            id = 12,
            tmdbId = "2344",
            name = "The Matrix Collection",
            posterUrl = "/covers/collection.jpg",
            backdropUrl = null,
            overview = "Matrix films.",
        )

    private fun tmdbCollection() =
        TmdbApiClient.TmdbMovieCollectionDetails(
            tmdbId = "2344",
            name = "The Matrix Collection",
            overview = "Matrix films.",
            posterPath = "/poster.jpg",
            backdropPath = "/backdrop.jpg",
            parts =
                listOf(
                    TmdbApiClient.TmdbMovieCollectionPart(
                        tmdbId = "603",
                        title = "The Matrix",
                        originalTitle = "The Matrix",
                        overview = "Reality bends.",
                        releaseYear = 1999,
                        posterPath = "/matrix.jpg",
                        backdropPath = null,
                    ),
                    TmdbApiClient.TmdbMovieCollectionPart(
                        tmdbId = "604",
                        title = "The Matrix Reloaded",
                        originalTitle = "The Matrix Reloaded",
                        overview = "The story continues.",
                        releaseYear = 2003,
                        posterPath = "/reloaded.jpg",
                        backdropPath = null,
                    ),
                ),
        )
}
