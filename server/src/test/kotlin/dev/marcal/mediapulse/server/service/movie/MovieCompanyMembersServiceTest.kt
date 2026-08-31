package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.crud.MovieCompanyMembersRepository
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class MovieCompanyMembersServiceTest {
    private val repository = mockk<MovieCompanyMembersRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiClient>()
    private val catalog = mockk<ManualMovieCatalogService>()
    private val tx = mockk<TxUtil>()
    private val service = MovieCompanyMembersService(repository, tmdb, catalog, tx)

    init {
        every { tx.inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() }
    }

    @Test
    fun `should return company members from local snapshot`() {
        every { repository.findCompany(12) } returns company()
        every { repository.findMembers(12) } returns
            listOf(
                MovieCompanyMembersRepository.MemberRecord(
                    "129",
                    "Spirited Away",
                    "Sen to Chihiro no kamikakushi",
                    2001,
                    null,
                    "/covers/spirited-away.jpg",
                    7,
                    "spirited-away",
                ),
                MovieCompanyMembersRepository.MemberRecord(
                    "128",
                    "Princess Mononoke",
                    null,
                    1997,
                    null,
                    null,
                    null,
                    null,
                ),
            )

        val response = service.getMembers(12)

        assertEquals(2, response.members.size)
        assertTrue(response.members[0].inCatalog)
        assertFalse(response.members[1].inCatalog)
        verify(exactly = 0) { tmdb.fetchCompanyMovies(any()) }
    }

    @Test
    fun `should persist provider response as an atomic snapshot`() {
        val members = slot<List<MovieCompanyMembersRepository.MemberSnapshot>>()
        every { repository.findCompany(12) } returns company()
        every { tmdb.fetchCompanyMovies("10342") } returns
            TmdbApiClient.TmdbCompanyMovies(
                companyTmdbId = "10342",
                movies =
                    listOf(
                        TmdbApiClient.TmdbMovieSearchItem(
                            tmdbId = "129",
                            title = "Spirited Away",
                            originalTitle = null,
                            overview = null,
                            releaseYear = 2001,
                            posterPath = "/poster.jpg",
                        ),
                    ),
            )
        every { catalog.buildTmdbImageUrl("/poster.jpg") } returns "https://image.tmdb.org/poster.jpg"
        every { repository.replaceSnapshot(12, capture(members)) } returns Unit

        assertTrue(service.refreshMembers(12))

        assertEquals(listOf("129"), members.captured.map { it.tmdbId })
        assertEquals("https://image.tmdb.org/poster.jpg", members.captured.first().posterUrl)
    }

    @Test
    fun `should record failed provider attempt`() {
        every { repository.findCompany(12) } returns company()
        every { tmdb.fetchCompanyMovies("10342") } returns null

        assertFalse(service.refreshMembers(12))

        verify { repository.markSyncFailure(12, "TMDb company movies unavailable") }
        verify(exactly = 0) { repository.replaceSnapshot(any(), any()) }
    }

    private fun company() =
        MovieCompanyMembersRepository.CompanyRecord(
            id = 12,
            tmdbId = "10342",
            name = "Studio Ghibli",
            logoUrl = null,
            originCountry = "JP",
        )
}
