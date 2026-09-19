package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.movies.PersonShowFilmographyMemberDto
import dev.marcal.mediapulse.server.api.movies.PersonShowFilmographyResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.MediaType
import dev.marcal.mediapulse.server.service.tv.ManualShowCatalogService
import dev.marcal.mediapulse.server.service.tv.ShowCreditsService
import dev.marcal.mediapulse.server.util.TxUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.concurrent.atomic.AtomicBoolean

@Service
class PersonShowFilmographyService(
    private val repository: PersonFilmographyRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualShowCatalogService: ManualShowCatalogService,
    private val tx: TxUtil,
    private val showCreditsService: ShowCreditsService,
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
            "Story Editor",
        )

    @Transactional(readOnly = true)
    fun getFilmography(personId: Long): PersonShowFilmographyResponse {
        val person = findPerson(personId)
        return PersonShowFilmographyResponse(
            person.id,
            person.tmdbId,
            person.name,
            person.profileUrl,
            repository.findMembers(personId, MediaType.SHOW).map { member ->
                val item = member.snapshot
                val availableCategories = categories(item.roleLabel)
                PersonShowFilmographyMemberDto(
                    item.tmdbId,
                    item.title,
                    item.originalTitle,
                    item.year,
                    item.overview,
                    item.posterUrl,
                    item.backdropUrl,
                    "https://www.themoviedb.org/tv/${item.tmdbId}",
                    member.localId,
                    member.localSlug,
                    member.localId != null,
                    item.roleLabel,
                    when {
                        member.localId == null -> "OUTSIDE_CATALOG"
                        member.watchedCount == 0L -> "NOT_STARTED"
                        member.totalCount > 0 && member.watchedCount >= member.totalCount -> "WATCHED"
                        else -> "IN_PROGRESS"
                    },
                    member.linkedCategories.sorted(),
                    (availableCategories - member.linkedCategories).sorted(),
                )
            },
            repository.isCompacted(personId, MediaType.SHOW),
        )
    }

    fun enrichPending(limit: Int = 25): BatchResult {
        if (!running.compareAndSet(false, true)) return BatchResult(0, 0, 0)
        return try {
            val candidates = repository.findPendingPersonIds(MediaType.SHOW, limit.coerceIn(1, 200))
            val completed = candidates.count(::refreshFilmography)
            BatchResult(candidates.size, completed, candidates.size - completed)
        } finally {
            running.set(false)
        }
    }

    fun refreshFilmography(personId: Long): Boolean {
        val person = findPerson(personId)
        val credits =
            tmdbApiClient.fetchPersonTvCredits(person.tmdbId) ?: run {
                tx.inTx { repository.markFailure(personId, MediaType.SHOW, "TMDb show filmography unavailable") }
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
                        credit.posterPath?.let(manualShowCatalogService::buildTmdbImageUrl),
                        credit.backdropPath?.let(manualShowCatalogService::buildTmdbImageUrl),
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
                        credit.posterPath?.let(manualShowCatalogService::buildTmdbImageUrl),
                        credit.backdropPath?.let(manualShowCatalogService::buildTmdbImageUrl),
                        mutableListOf(),
                    )
                }
            addRole(item, credit.job ?: credit.department ?: "Equipe")
        }
        persist(personId, merged.values)
        return true
    }

    fun refreshAndGetFilmography(personId: Long): PersonShowFilmographyResponse {
        if (!refreshFilmography(personId)) throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "TMDb show filmography unavailable")
        return getFilmography(personId)
    }

    fun linkLocalShow(
        personId: Long,
        showId: Long,
        category: String,
    ) {
        val member =
            repository.findMembers(personId, MediaType.SHOW).firstOrNull { it.localId == showId }
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Série não encontrada na filmografia local")
        val normalized = category.trim().uppercase()
        if (normalized !in categories(member.snapshot.roleLabel) || normalized in member.linkedCategories) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Categoria indisponível para este vínculo")
        }
        showCreditsService.linkExistingPerson(showId, personId, normalized, roleFor(member.snapshot.roleLabel, normalized))
        pruneResolvedCompactedMember(personId, showId)
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
                    snapshotRoles(it.roles).joinToString(" · "),
                )
            }
        tx.inTx { repository.replaceSnapshot(personId, MediaType.SHOW, snapshots) }
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

    private fun categories(roleLabel: String): Set<String> {
        val roles = roleLabel.split(" · ")
        return buildSet {
            if (roles.any { it == "Director" }) add("DIRECTING")
            if (roles.any { it in relevantCrewJobs - "Director" }) add("WRITING")
            if (roles.any { it !in relevantCrewJobs }) add("CAST")
        }
    }

    private fun snapshotRoles(roles: List<String>): List<String> =
        listOfNotNull(
            roles.firstOrNull { it == "Director" },
            roles.firstOrNull { it in relevantCrewJobs - "Director" },
            roles.firstOrNull { it !in relevantCrewJobs },
        )

    private fun roleFor(
        roleLabel: String,
        category: String,
    ): String? = if (category == "CAST") roleLabel.split(" · ").firstOrNull { it !in relevantCrewJobs } else null

    private fun pruneResolvedCompactedMember(
        personId: Long,
        showId: Long,
    ) {
        if (!repository.isCompacted(personId, MediaType.SHOW)) return
        val member = repository.findMembers(personId, MediaType.SHOW).firstOrNull { it.localId == showId } ?: return
        if ((categories(member.snapshot.roleLabel) - member.linkedCategories).isEmpty()) {
            tx.inTx { repository.deleteMember(personId, MediaType.SHOW, member.snapshot.tmdbId) }
        }
    }
}
