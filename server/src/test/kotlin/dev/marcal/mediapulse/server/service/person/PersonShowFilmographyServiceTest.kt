package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.movies.PersonFilmographySyncState
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.MediaType
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.SyncStatus
import dev.marcal.mediapulse.server.service.tv.ManualShowCatalogService
import dev.marcal.mediapulse.server.service.tv.ShowCreditsService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PersonShowFilmographyServiceTest {
    private val repository = mockk<PersonFilmographyRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiClient>()
    private val catalog = mockk<ManualShowCatalogService>(relaxed = true)
    private val tx = mockk<TxUtil>()
    private val credits = mockk<ShowCreditsService>(relaxed = true)
    private val service = PersonShowFilmographyService(repository, tmdb, catalog, tx, credits)

    init {
        every { repository.findSyncStatus(any(), any()) } returns SyncStatus()

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
    fun `link local show should use an available category and prune a resolved compacted member`() {
        val snapshot = PersonFilmographyRepository.MemberSnapshot("10", "Show", null, 2020, null, null, null, "Writer · Character")
        every { repository.findMembers(44, MediaType.SHOW) } returnsMany
            listOf(
                listOf(PersonFilmographyRepository.MemberRecord(snapshot, 8, "show", 1, 10, setOf("CAST"))),
                listOf(PersonFilmographyRepository.MemberRecord(snapshot, 8, "show", 1, 10, setOf("CAST", "WRITING"))),
            )
        every { repository.isCompacted(44, MediaType.SHOW) } returns true

        service.linkLocalShow(44, 8, "WRITING")

        verify { credits.linkExistingPerson(8, 44, "WRITING", null) }
        verify { repository.deleteMember(44, MediaType.SHOW, "10") }
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
                        TmdbApiClient.TmdbPersonTvCrewCredit(
                            "901",
                            "Show A",
                            "Show A",
                            null,
                            2019,
                            null,
                            null,
                            "Production",
                            "Executive Producer",
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
        assertFalse(
            snapshot.captured
                .single()
                .roleLabel
                .contains("Executive Producer"),
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

    @Test
    fun `ensure reuses complete empty snapshot without resetting retention`() {
        every { repository.findPerson(44) } returns person()
        every { repository.findMembers(44, MediaType.SHOW) } returns emptyList()
        every { repository.findSyncStatus(44, MediaType.SHOW) } returns SyncStatus(synced = true)

        val response = service.ensureFilmography(44)

        assertEquals(PersonFilmographySyncState.COMPLETE, response.syncState)
        assertTrue(response.members.isEmpty())
        verify(exactly = 0) { tmdb.fetchPersonTvCredits(any()) }
        verify(exactly = 0) { repository.replaceSnapshot(any(), any(), any()) }
    }

    @Test
    fun `ensure restores compacted snapshot once and reuses it on next exploration`() {
        val status = AtomicReference(SyncStatus(synced = true, compacted = true))
        every { repository.findPerson(44) } returns person()
        every { repository.findSyncStatus(44, MediaType.SHOW) } answers { status.get() }
        every { tmdb.fetchPersonTvCredits("138") } returns TmdbApiClient.TmdbPersonTvCredits(emptyList(), emptyList())
        every { repository.replaceSnapshot(44, MediaType.SHOW, any()) } answers { status.set(SyncStatus(synced = true)) }

        val restored = service.ensureFilmography(44)
        service.ensureFilmography(44)

        assertEquals(PersonFilmographySyncState.COMPLETE, restored.syncState)
        assertFalse(restored.compacted)
        verify(exactly = 1) { tmdb.fetchPersonTvCredits("138") }
    }

    @Test
    fun `ensure respects failure cooldown but explicit retry can refresh`() {
        every { repository.findPerson(44) } returns person()
        every { repository.findSyncStatus(44, MediaType.SHOW) } returns SyncStatus(compacted = true, failed = true, retryReady = false)
        every { tmdb.fetchPersonTvCredits("138") } returns TmdbApiClient.TmdbPersonTvCredits(emptyList(), emptyList())

        assertEquals(PersonFilmographySyncState.FAILED, service.ensureFilmography(44).syncState)
        verify(exactly = 0) { tmdb.fetchPersonTvCredits(any()) }
        assertTrue(service.refreshFilmography(44))
        verify(exactly = 1) { tmdb.fetchPersonTvCredits("138") }
    }

    @Test
    fun `ensure downloads never synced filmography and records provider failure without replacing data`() {
        every { repository.findPerson(44) } returns person()
        every { tmdb.fetchPersonTvCredits("138") } throws IllegalStateException("provider timeout")
        every { repository.markFailure(44, MediaType.SHOW, "provider timeout") } answers {
            every { repository.findSyncStatus(44, MediaType.SHOW) } returns SyncStatus(failed = true, retryReady = false)
        }

        assertEquals(PersonFilmographySyncState.FAILED, service.ensureFilmography(44).syncState)
        service.ensureFilmography(44)

        verify(exactly = 1) { tmdb.fetchPersonTvCredits("138") }
        verify(exactly = 0) { repository.replaceSnapshot(any(), any(), any()) }
    }

    @Test
    fun `worker rechecks availability after simultaneous exploration and avoids duplicate download`() {
        val status = AtomicReference(SyncStatus())
        val providerStarted = CountDownLatch(1)
        val workerSelected = CountDownLatch(1)
        val releaseProvider = CountDownLatch(1)
        every { repository.findPerson(44) } returns person()
        every { repository.findSyncStatus(44, MediaType.SHOW) } answers { status.get() }
        every { repository.findPendingPersonIds(MediaType.SHOW, 25) } answers {
            workerSelected.countDown()
            listOf(44)
        }
        every { tmdb.fetchPersonTvCredits("138") } answers {
            providerStarted.countDown()
            check(releaseProvider.await(5, TimeUnit.SECONDS))
            TmdbApiClient.TmdbPersonTvCredits(emptyList(), emptyList())
        }
        every { repository.replaceSnapshot(44, MediaType.SHOW, any()) } answers { status.set(SyncStatus(synced = true)) }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val page = executor.submit<Any> { service.ensureFilmography(44) }
            assertTrue(providerStarted.await(5, TimeUnit.SECONDS))
            val worker = executor.submit<Any> { service.enrichPending() }
            assertTrue(workerSelected.await(5, TimeUnit.SECONDS))
            releaseProvider.countDown()
            page.get(5, TimeUnit.SECONDS)
            worker.get(5, TimeUnit.SECONDS)
            verify(exactly = 1) { tmdb.fetchPersonTvCredits("138") }
        } finally {
            releaseProvider.countDown()
            executor.shutdownNow()
        }
    }

    private fun person() = PersonFilmographyRepository.PersonRecord(44, "138", "Quentin Tarantino", null)
}
