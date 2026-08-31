package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.crud.MovieCollectionCrudRepository
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
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
