package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.MediaType
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class PersonFilmographyCompactionServiceTest {
    private val repository = mockk<PersonFilmographyRepository>()
    private val tx = mockk<TxUtil>()
    private val service = PersonFilmographyCompactionService(repository, tx)

    init {
        every { tx.inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() }
    }

    @Test
    fun `compacts eligible movie and show snapshots and reports removed members`() {
        every { repository.findCompactionCandidatePersonIds(100) } returns listOf(10, 20)
        every { repository.compactIfEligible(10, MediaType.MOVIE) } returns
            PersonFilmographyRepository.CompactionResult(MediaType.MOVIE, 30)
        every { repository.compactIfEligible(10, MediaType.SHOW) } returns null
        every { repository.compactIfEligible(20, MediaType.MOVIE) } returns null
        every { repository.compactIfEligible(20, MediaType.SHOW) } returns
            PersonFilmographyRepository.CompactionResult(MediaType.SHOW, 12)

        val result = service.compact()

        assertEquals(2, result.candidates)
        assertEquals(2, result.processedPeople)
        assertEquals(2, result.compactedSnapshots)
        assertEquals(42, result.removedMembers)
    }

    @Test
    fun `normalizes administrative limit`() {
        every { repository.findCompactionCandidatePersonIds(1000) } returns emptyList()

        val result = service.compact(5000)

        assertEquals(1000, result.requestedLimit)
        verify { repository.findCompactionCandidatePersonIds(1000) }
    }
}
