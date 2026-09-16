package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.movies.PersonFilmographyCompactionResponse
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.MediaType
import dev.marcal.mediapulse.server.util.TxUtil
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class PersonFilmographyCompactionService(
    private val repository: PersonFilmographyRepository,
    private val tx: TxUtil,
) {
    private val running = AtomicBoolean(false)

    fun compact(limit: Int = 100): PersonFilmographyCompactionResponse {
        val normalizedLimit = limit.coerceIn(1, 1000)
        if (!running.compareAndSet(false, true)) {
            return PersonFilmographyCompactionResponse(normalizedLimit, 0, 0, 0, 0)
        }
        return try {
            val candidates = repository.findCompactionCandidatePersonIds(normalizedLimit)
            val resultsByPerson =
                candidates.map { personId ->
                    tx.inTx {
                        MediaType.entries.mapNotNull { mediaType -> repository.compactIfEligible(personId, mediaType) }
                    }
                }
            val results = resultsByPerson.flatten()
            PersonFilmographyCompactionResponse(
                requestedLimit = normalizedLimit,
                candidates = candidates.size,
                processedPeople = resultsByPerson.count { it.isNotEmpty() },
                compactedSnapshots = results.size,
                removedMembers = results.sumOf { it.removedMembers },
            )
        } finally {
            running.set(false)
        }
    }
}
