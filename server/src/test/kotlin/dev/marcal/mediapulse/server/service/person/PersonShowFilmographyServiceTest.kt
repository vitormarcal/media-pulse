package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.MediaType
import dev.marcal.mediapulse.server.service.tv.ManualShowCatalogService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonShowFilmographyServiceTest {
    private val repository = mockk<PersonFilmographyRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiClient>()
    private val catalog = mockk<ManualShowCatalogService>(relaxed = true)
    private val tx = mockk<TxUtil>()
    private val service = PersonShowFilmographyService(repository, tmdb, catalog, tx)

    init {
        every { tx.inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() }
    }

    @Test
    fun `local read should not call tmdb`() {
        every { repository.findPerson(44) } returns person()
        every { repository.findMembers(44, MediaType.SHOW) } returns emptyList()

        service.getFilmography(44)

        verify(exactly = 0) { tmdb.fetchPersonTvCredits(any()) }
    }

    @Test
    fun `refresh should persist merged show snapshot`() {
        val snapshot = slot<List<PersonFilmographyRepository.MemberSnapshot>>()
        every { repository.findPerson(44) } returns person()
        every { tmdb.fetchPersonTvCredits("138") } returns
            TmdbApiClient.TmdbPersonTvCredits(
                cast =
                    listOf(
                        TmdbApiClient.TmdbPersonTvCastCredit(
                            "901",
                            "Show A",
                            "Show A",
                            null,
                            2019,
                            null,
                            null,
                            "Narrador",
                            6,
                        ),
                    ),
                crew =
                    listOf(
                        TmdbApiClient.TmdbPersonTvCrewCredit(
                            "901",
                            "Show A",
                            "Show A",
                            null,
                            2019,
                            null,
                            null,
                            "Writing",
                            "Writer",
                        ),
                    ),
            )
        every { repository.replaceSnapshot(44, MediaType.SHOW, capture(snapshot)) } returns Unit

        assertTrue(service.refreshFilmography(44))
        assertTrue(
            snapshot.captured
                .single()
                .roleLabel
                .contains("Narrador"),
        )
        assertTrue(
            snapshot.captured
                .single()
                .roleLabel
                .contains("Writer"),
        )
    }

    @Test
    fun `failed refresh should preserve snapshot and record attempt`() {
        every { repository.findPerson(44) } returns person()
        every { tmdb.fetchPersonTvCredits("138") } returns null

        assertFalse(service.refreshFilmography(44))
        verify { repository.markFailure(44, MediaType.SHOW, "TMDb show filmography unavailable") }
        verify(exactly = 0) { repository.replaceSnapshot(any(), any(), any()) }
    }

    private fun person() = PersonFilmographyRepository.PersonRecord(44, "138", "Quentin Tarantino", null)
}
