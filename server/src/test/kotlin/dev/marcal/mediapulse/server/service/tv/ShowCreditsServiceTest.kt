package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.TvShowQueryRepository
import dev.marcal.mediapulse.server.repository.crud.PersonRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.ShowCreditsCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate
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
}
