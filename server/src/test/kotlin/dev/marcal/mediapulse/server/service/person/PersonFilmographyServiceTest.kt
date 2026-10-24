package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.movies.PersonFilmographySyncState
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.MediaType
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.SyncStatus
import dev.marcal.mediapulse.server.service.movie.ManualMovieCatalogService
import dev.marcal.mediapulse.server.service.movie.MovieCreditsService
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

class PersonFilmographyServiceTest {
    private val repository = mockk<PersonFilmographyRepository>(relaxed = true)
    private val tmdb = mockk<TmdbApiClient>()
    private val catalog = mockk<ManualMovieCatalogService>(relaxed = true)
    private val tx = mockk<TxUtil>()
    private val credits = mockk<MovieCreditsService>(relaxed = true)
    private val service = PersonFilmographyService(repository, tmdb, catalog, tx, credits)

    init {
        every { repository.findSyncStatus(any(), any()) } returns SyncStatus()

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
    fun `link local movie should use an available category and prune a resolved compacted member`() {
        val snapshot = PersonFilmographyRepository.MemberSnapshot("10", "Movie", null, 2020, null, null, null, "Director · Hero")
        every { repository.findMembers(44, MediaType.MOVIE) } returnsMany
            listOf(
                listOf(PersonFilmographyRepository.MemberRecord(snapshot, 7, "movie", 1, 0, setOf("CAST"))),
                listOf(PersonFilmographyRepository.MemberRecord(snapshot, 7, "movie", 1, 0, setOf("CAST", "DIRECTING"))),
            )
        every { repository.isCompacted(44, MediaType.MOVIE) } returns true

        service.linkLocalMovie(44, 7, "DIRECTING")

        verify { credits.linkExistingPerson(7, match { it.personId == 44L && it.group == "DIRECTORS" }) }
        verify { repository.deleteMember(44, MediaType.MOVIE, "10") }
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
                        TmdbApiClient.TmdbPersonMovieCrewCredit(
                            "101",
                            "Movie A",
                            "Movie A",
                            null,
                            2001,
                            null,
                            null,
                            "Production",
                            "Producer",
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
        assertFalse(
            snapshot.captured
                .single()
                .roleLabel
                .contains("Producer"),
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

    @Test
    fun `ensure reuses complete empty snapshot without resetting retention`() {
        every { repository.findPerson(44) } returns person()
        every { repository.findMembers(44, MediaType.MOVIE) } returns emptyList()
        every { repository.findSyncStatus(44, MediaType.MOVIE) } returns SyncStatus(synced = true)

        val response = service.ensureFilmography(44)

        assertEquals(PersonFilmographySyncState.COMPLETE, response.syncState)
        assertTrue(response.members.isEmpty())
        verify(exactly = 0) { tmdb.fetchPersonMovieCredits(any()) }
        verify(exactly = 0) { repository.replaceSnapshot(any(), any(), any()) }
    }

    @Test
    fun `ensure restores compacted snapshot once and reuses it on next exploration`() {
        val status = AtomicReference(SyncStatus(synced = true, compacted = true))
        every { repository.findPerson(44) } returns person()
        every { repository.findSyncStatus(44, MediaType.MOVIE) } answers { status.get() }
        every { tmdb.fetchPersonMovieCredits("138") } returns TmdbApiClient.TmdbPersonMovieCredits(emptyList(), emptyList())
        every { repository.replaceSnapshot(44, MediaType.MOVIE, any()) } answers { status.set(SyncStatus(synced = true)) }

        val restored = service.ensureFilmography(44)
        service.ensureFilmography(44)

        assertEquals(PersonFilmographySyncState.COMPLETE, restored.syncState)
        assertFalse(restored.compacted)
        verify(exactly = 1) { tmdb.fetchPersonMovieCredits("138") }
    }

    @Test
    fun `ensure respects failure cooldown but explicit retry can refresh`() {
        every { repository.findPerson(44) } returns person()
        every { repository.findSyncStatus(44, MediaType.MOVIE) } returns SyncStatus(compacted = true, failed = true, retryReady = false)
        every { tmdb.fetchPersonMovieCredits("138") } returns TmdbApiClient.TmdbPersonMovieCredits(emptyList(), emptyList())

        assertEquals(PersonFilmographySyncState.FAILED, service.ensureFilmography(44).syncState)
        verify(exactly = 0) { tmdb.fetchPersonMovieCredits(any()) }
        assertTrue(service.refreshFilmography(44))
        verify(exactly = 1) { tmdb.fetchPersonMovieCredits("138") }
    }

    @Test
    fun `ensure downloads never synced filmography and records provider failure without replacing data`() {
        every { repository.findPerson(44) } returns person()
        every { tmdb.fetchPersonMovieCredits("138") } throws IllegalStateException("provider timeout")
        every { repository.markFailure(44, MediaType.MOVIE, "provider timeout") } answers {
            every { repository.findSyncStatus(44, MediaType.MOVIE) } returns SyncStatus(failed = true, retryReady = false)
        }

        assertEquals(PersonFilmographySyncState.FAILED, service.ensureFilmography(44).syncState)
        service.ensureFilmography(44)

        verify(exactly = 1) { tmdb.fetchPersonMovieCredits("138") }
        verify(exactly = 0) { repository.replaceSnapshot(any(), any(), any()) }
    }

    @Test
    fun `worker rechecks availability after simultaneous exploration and avoids duplicate download`() {
        val status = AtomicReference(SyncStatus())
        val providerStarted = CountDownLatch(1)
        val workerSelected = CountDownLatch(1)
        val releaseProvider = CountDownLatch(1)
        every { repository.findPerson(44) } returns person()
        every { repository.findSyncStatus(44, MediaType.MOVIE) } answers { status.get() }
        every { repository.findPendingPersonIds(MediaType.MOVIE, 25) } answers {
            workerSelected.countDown()
            listOf(44)
        }
        every { tmdb.fetchPersonMovieCredits("138") } answers {
            providerStarted.countDown()
            check(releaseProvider.await(5, TimeUnit.SECONDS))
            TmdbApiClient.TmdbPersonMovieCredits(emptyList(), emptyList())
        }
        every { repository.replaceSnapshot(44, MediaType.MOVIE, any()) } answers { status.set(SyncStatus(synced = true)) }
        val executor = Executors.newFixedThreadPool(2)
        try {
            val page = executor.submit<Any> { service.ensureFilmography(44) }
            assertTrue(providerStarted.await(5, TimeUnit.SECONDS))
            val worker = executor.submit<Any> { service.enrichPending() }
            assertTrue(workerSelected.await(5, TimeUnit.SECONDS))
            releaseProvider.countDown()
            page.get(5, TimeUnit.SECONDS)
            worker.get(5, TimeUnit.SECONDS)
            verify(exactly = 1) { tmdb.fetchPersonMovieCredits("138") }
        } finally {
            releaseProvider.countDown()
            executor.shutdownNow()
        }
    }

    private fun person() = PersonFilmographyRepository.PersonRecord(44, "138", "Quentin Tarantino", null)
}
