package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowCreditsSyncResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.person.Person
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.TvShowQueryRepository
import dev.marcal.mediapulse.server.repository.crud.PersonRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditsCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import kotlin.test.assertEquals

class ShowCreditsServiceTest {
    private val showRepository = mockk<TvShowRepository>()
    private val personRepository = mockk<PersonRepository>()
    private val assignments = mockk<ShowCreditAssignmentRepository>()
    private val creditsRepository = mockk<ShowCreditsCrudRepository>(relaxed = true)
    private val queryRepository = mockk<TvShowQueryRepository>()
    private val tmdb = mockk<TmdbApiClient>()
    private val catalog = mockk<ManualShowCatalogService>()
    private val transactions = mockk<TransactionTemplate>()
    private val service =
        ShowCreditsService(
            showRepository,
            personRepository,
            assignments,
            creditsRepository,
            queryRepository,
            tmdb,
            catalog,
            transactions,
        )

    @Test
    fun `automatic batch should record failure and continue`() {
        val candidate = ShowCreditsCrudRepository.ShowCreditsSyncCandidate(9, "909")
        var transactionCalls = 0
        every { creditsRepository.countPendingTmdbSyncCandidates() } returns 1
        every { creditsRepository.findTmdbSyncCandidates(25) } returns listOf(candidate)
        every { transactions.execute<Any?>(any()) } answers {
            transactionCalls++
            if (transactionCalls == 1) throw IllegalStateException("provider unavailable")
            val callback = arg<org.springframework.transaction.support.TransactionCallback<Any?>>(0)
            callback.doInTransaction(mockk(relaxed = true))
        }

        val result = service.syncAllFromTmdb(25)

        assertEquals(1, result.failed)
        verify { creditsRepository.markCreditsSyncFailure(9, "provider unavailable") }
    }

    @Test
    fun `automatic batch should continue when recording a failure also fails`() {
        val failedCandidate = ShowCreditsCrudRepository.ShowCreditsSyncCandidate(9, "909")
        val successfulCandidate = ShowCreditsCrudRepository.ShowCreditsSyncCandidate(10, "1010")
        var transactionCalls = 0
        every { creditsRepository.countPendingTmdbSyncCandidates() } returns 2
        every { creditsRepository.findTmdbSyncCandidates(25) } returns listOf(failedCandidate, successfulCandidate)
        every { creditsRepository.markCreditsSyncFailure(9, "provider unavailable") } throws
            IllegalStateException("database unavailable")
        every { transactions.execute<Any?>(any()) } answers {
            transactionCalls++
            when (transactionCalls) {
                1 -> throw IllegalStateException("provider unavailable")
                2 -> {
                    val callback = arg<org.springframework.transaction.support.TransactionCallback<Any?>>(0)
                    callback.doInTransaction(mockk(relaxed = true))
                }
                else -> mockk<ShowCreditsSyncResponse>()
            }
        }

        val result = service.syncAllFromTmdb(25)

        assertEquals(2, result.processed)
        assertEquals(1, result.synced)
        assertEquals(1, result.failed)
        assertEquals(3, transactionCalls)
        verify { creditsRepository.markCreditsSyncFailure(9, "provider unavailable") }
    }

    @Test
    fun `sync should persist only directing and writing crew`() {
        val show = TvShow(id = 12, originalTitle = "Show 12", fingerprint = "show-12", tmdbId = "1212")
        val capturedCredits = slot<List<ShowCreditAssignmentRepository.UpsertShowCreditRequest>>()

        every { showRepository.findById(12) } returns Optional.of(show)
        every { queryRepository.getShowPeople(12) } returns emptyList()
        every { personRepository.findByTmdbId(any()) } returns null
        every { personRepository.save(any()) } answers { firstArg<Person>().copy(id = firstArg<Person>().tmdbId.toLong()) }
        every { assignments.replaceForShow(12, capture(capturedCredits)) } returns Unit
        every { tmdb.fetchShowCredits("1212") } returns
            TmdbApiClient.TmdbShowCredits(
                cast = emptyList(),
                crew =
                    listOf(
                        showCrew("1", "Director", "Directing"),
                        showCrew("2", "Story Editor", "Writing"),
                        showCrew("3", "Executive Producer", "Production"),
                        showCrew("4", "Original Music Composer", "Sound"),
                    ),
            )

        val response = service.syncFromTmdb(12)

        assertEquals(2, response.syncedCount)
        assertEquals(listOf("Director", "Story Editor"), capturedCredits.captured.map { it.job })
        verify(exactly = 0) { personRepository.findByTmdbId("3") }
        verify(exactly = 0) { personRepository.findByTmdbId("4") }
    }

    private fun showCrew(
        tmdbId: String,
        job: String,
        department: String,
    ) = TmdbApiClient.TmdbShowCrewCredit(
        tmdbId = tmdbId,
        name = "$job Person",
        department = department,
        job = job,
        profilePath = null,
    )
}
