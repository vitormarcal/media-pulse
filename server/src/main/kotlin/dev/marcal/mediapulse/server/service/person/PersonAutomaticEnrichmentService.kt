package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.PersonProfileRepository
import dev.marcal.mediapulse.server.repository.crud.PersonRepository
import dev.marcal.mediapulse.server.service.movie.ManualMovieCatalogService
import dev.marcal.mediapulse.server.util.TxUtil
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant
import java.util.concurrent.atomic.AtomicBoolean

@Service
class PersonAutomaticEnrichmentService(
    private val personRepository: PersonRepository,
    private val personProfileRepository: PersonProfileRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualMovieCatalogService: ManualMovieCatalogService,
    private val tx: TxUtil,
) {
    data class BatchResult(
        val candidates: Int,
        val completed: Int,
        val pending: Int,
    )

    private val logger = LoggerFactory.getLogger(javaClass)
    private val running = AtomicBoolean(false)

    fun enrichPending(limit: Int = 25): BatchResult {
        if (!running.compareAndSet(false, true)) return BatchResult(0, 0, 0)

        return try {
            val candidates = personProfileRepository.findPendingPersonIds(limit.coerceIn(1, 200))
            var completed = 0
            candidates.forEach { personId ->
                if (enrichPerson(personId)) completed++
            }
            BatchResult(candidates.size, completed, candidates.size - completed)
        } finally {
            running.set(false)
        }
    }

    fun enrichPerson(personId: Long): Boolean {
        val person = personRepository.findById(personId).orElse(null) ?: return false
        val attemptedAt = Instant.now()
        val profile = tmdbApiClient.fetchPersonDetails(person.tmdbId)

        if (profile == null) {
            tx.inTx {
                personRepository.save(
                    person.copy(
                        tmdbSyncAttemptedAt = attemptedAt,
                        tmdbSyncError = "TMDb profile unavailable",
                        updatedAt = attemptedAt,
                    ),
                )
            }
            logger.info("Person enrichment pending | personId={} tmdbId={}", person.id, person.tmdbId)
            return false
        }

        tx.inTx {
            personRepository.save(
                person.copy(
                    profileUrl = profile.profilePath?.let(manualMovieCatalogService::buildTmdbImageUrl) ?: person.profileUrl,
                    biography = profile.biography,
                    birthday = profile.birthday,
                    deathday = profile.deathday,
                    placeOfBirth = profile.placeOfBirth,
                    knownForDepartment = profile.knownForDepartment,
                    homepage = profile.homepage,
                    imdbId = profile.imdbId,
                    popularity = profile.popularity,
                    tmdbSyncedAt = attemptedAt,
                    tmdbSyncAttemptedAt = attemptedAt,
                    tmdbSyncError = null,
                    updatedAt = attemptedAt,
                ),
            )
            personProfileRepository.replaceAliases(person.id, profile.alsoKnownAs)
        }
        return true
    }
}
