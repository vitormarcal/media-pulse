package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.movies.PersonFilmographyMemberDto
import dev.marcal.mediapulse.server.api.movies.PersonFilmographyResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.MediaType
import dev.marcal.mediapulse.server.service.movie.ManualMovieCatalogService
import dev.marcal.mediapulse.server.util.TxUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.atomic.AtomicBoolean

@Service
class PersonFilmographyService(
    private val repository: PersonFilmographyRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualMovieCatalogService: ManualMovieCatalogService,
    private val tx: TxUtil,
) {
    data class BatchResult(
        val candidates: Int,
        val completed: Int,
        val pending: Int,
    )

    private data class Item(
        val tmdbId: String,
        val title: String,
        val originalTitle: String?,
        val overview: String?,
        val year: Int?,
        val posterUrl: String?,
        val backdropUrl: String?,
        val roles: MutableList<String>,
    )

    private val running = AtomicBoolean(false)
    private val relevantCrewJobs =
        setOf(
            "Director",
            "Writer",
            "Screenplay",
            "Story",
            "Editor",
            "Producer",
            "Director of Photography",
            "Original Music Composer",
        )

    @Transactional(readOnly = true)
    fun getFilmography(personId: Long): PersonFilmographyResponse {
        val person = findPerson(personId)
        return PersonFilmographyResponse(
            person.id,
            person.tmdbId,
            person.name,
            person.profileUrl,
            repository.findMembers(personId, MediaType.MOVIE).map { member ->
                val item = member.snapshot
                PersonFilmographyMemberDto(
                    item.tmdbId,
                    item.title,
                    item.originalTitle,
                    item.year,
                    item.overview,
                    item.posterUrl,
                    item.backdropUrl,
                    "https://www.themoviedb.org/movie/${item.tmdbId}",
                    member.localId,
                    member.localSlug,
                    member.localId != null,
                    item.roleLabel,
                )
            },
        )
    }

    fun enrichPending(limit: Int = 25): BatchResult {
        if (!running.compareAndSet(false, true)) return BatchResult(0, 0, 0)
        return try {
            val candidates = repository.findPendingPersonIds(MediaType.MOVIE, limit.coerceIn(1, 200))
            val completed = candidates.count(::refreshFilmography)
            BatchResult(candidates.size, completed, candidates.size - completed)
        } finally {
            running.set(false)
        }
    }

    fun refreshFilmography(personId: Long): Boolean {
        val person = findPerson(personId)
        val credits =
            tmdbApiClient.fetchPersonMovieCredits(person.tmdbId) ?: run {
                tx.inTx { repository.markFailure(personId, MediaType.MOVIE, "TMDb movie filmography unavailable") }
                return false
            }
        val merged = linkedMapOf<String, Item>()
        credits.cast.sortedBy { it.order ?: Int.MAX_VALUE }.forEach { credit ->
            val title = credit.title ?: credit.originalTitle ?: return@forEach
            val item =
                merged.getOrPut(credit.tmdbId) {
                    Item(
                        credit.tmdbId,
                        title,
                        credit.originalTitle,
                        credit.overview,
                        credit.releaseYear,
                        credit.posterPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                        credit.backdropPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                        mutableListOf(),
                    )
                }
            addRole(item, credit.character?.takeIf(String::isNotBlank) ?: "Elenco")
        }
        credits.crew.filter { it.job in relevantCrewJobs }.forEach { credit ->
            val title = credit.title ?: credit.originalTitle ?: return@forEach
            val item =
                merged.getOrPut(credit.tmdbId) {
                    Item(
                        credit.tmdbId,
                        title,
                        credit.originalTitle,
                        credit.overview,
                        credit.releaseYear,
                        credit.posterPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                        credit.backdropPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                        mutableListOf(),
                    )
                }
            addRole(item, credit.job ?: credit.department ?: "Equipe")
        }
        persist(personId, merged.values)
        return true
    }

    fun refreshAndGetFilmography(personId: Long): PersonFilmographyResponse {
        if (!refreshFilmography(personId)) throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "TMDb movie filmography unavailable")
        return getFilmography(personId)
    }

    private fun persist(
        personId: Long,
        items: Collection<Item>,
    ) {
        val snapshots =
            items.sortedWith(compareByDescending<Item> { it.year ?: Int.MIN_VALUE }.thenBy { it.title }).map {
                PersonFilmographyRepository.MemberSnapshot(
                    it.tmdbId,
                    it.title,
                    it.originalTitle,
                    it.year,
                    it.overview,
                    it.posterUrl,
                    it.backdropUrl,
                    it.roles.take(3).joinToString(" · "),
                )
            }
        tx.inTx { repository.replaceSnapshot(personId, MediaType.MOVIE, snapshots) }
    }

    private fun findPerson(personId: Long) =
        repository.findPerson(personId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Person not found")

    private fun addRole(
        item: Item,
        role: String,
    ) {
        if (role !in item.roles) item.roles.add(role)
    }
}
