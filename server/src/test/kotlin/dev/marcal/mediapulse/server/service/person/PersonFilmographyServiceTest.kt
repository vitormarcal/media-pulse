package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.MediaType
import dev.marcal.mediapulse.server.service.movie.ManualMovieCatalogService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonFilmographyServiceTest {
    private val repository = mockk<PersonFilmographyRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiClient>()
    private val catalog = mockk<ManualMovieCatalogService>(relaxed = true)
    private val tx = mockk<TxUtil>()
    private val service = PersonFilmographyService(repository, tmdb, catalog, tx)

    init {
        every { tx.inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() }
    }

    @Test
    fun `local read should not call tmdb`() {
        every { repository.findPerson(44) } returns person()
        every { repository.findMembers(44, MediaType.MOVIE) } returns emptyList()

        service.getFilmography(44)

        verify(exactly = 0) { tmdb.fetchPersonMovieCredits(any()) }
    }

    @Test
    fun `refresh should persist merged movie snapshot`() {
        val snapshot = slot<List<PersonFilmographyRepository.MemberSnapshot>>()
        every { repository.findPerson(44) } returns person()
        every { tmdb.fetchPersonMovieCredits("138") } returns
            TmdbApiClient.TmdbPersonMovieCredits(
                cast =
                    listOf(
                        TmdbApiClient.TmdbPersonMovieCastCredit(
                            "101",
                            "Movie A",
                            "Movie A",
                            null,
                            2001,
                            null,
                            null,
                            "Himself",
                            12,
                        ),
                    ),
                crew =
                    listOf(
                        TmdbApiClient.TmdbPersonMovieCrewCredit(
                            "101",
                            "Movie A",
                            "Movie A",
                            null,
                            2001,
                            null,
                            null,
                            "Directing",
                            "Director",
                        ),
                    ),
            )
        every { repository.replaceSnapshot(44, MediaType.MOVIE, capture(snapshot)) } returns Unit

        assertTrue(service.refreshFilmography(44))
        assertTrue(
            snapshot.captured
                .single()
                .roleLabel
                .contains("Himself"),
        )
        assertTrue(
            snapshot.captured
                .single()
                .roleLabel
                .contains("Director"),
        )
    }

    @Test
    fun `failed refresh should preserve snapshot and record attempt`() {
        every { repository.findPerson(44) } returns person()
        every { tmdb.fetchPersonMovieCredits("138") } returns null

        assertFalse(service.refreshFilmography(44))
        verify { repository.markFailure(44, MediaType.MOVIE, "TMDb movie filmography unavailable") }
        verify(exactly = 0) { repository.replaceSnapshot(any(), any(), any()) }
    }

    private fun person() = PersonFilmographyRepository.PersonRecord(44, "138", "Quentin Tarantino", null)
}
