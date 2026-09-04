package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowTermCreateRequest
import dev.marcal.mediapulse.server.api.shows.ShowTermDto
import dev.marcal.mediapulse.server.api.shows.ShowTermsBatchSyncResponse
import dev.marcal.mediapulse.server.api.shows.ShowTermsSyncResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.tv.ShowTerm
import dev.marcal.mediapulse.server.model.tv.ShowTermKind
import dev.marcal.mediapulse.server.model.tv.ShowTermSource
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.TvShowQueryRepository
import dev.marcal.mediapulse.server.repository.crud.ShowTermAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.ShowTermRepository
import dev.marcal.mediapulse.server.repository.crud.ShowTermsCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.transaction.support.TransactionTemplate
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class ShowTermsService(
    private val shows: TvShowRepository,
    private val query: TvShowQueryRepository,
    private val terms: ShowTermRepository,
    private val assignments: ShowTermAssignmentRepository,
    private val syncState: ShowTermsCrudRepository,
    private val tmdb: TmdbApiClient,
    private val transactions: TransactionTemplate,
) {
    @Transactional
    fun syncFromTmdb(showId: Long): ShowTermsSyncResponse {
        val show = requireShow(showId)
        val tmdbId = show.tmdbId ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Série sem vínculo TMDb")
        val details =
            tmdb.fetchShowDetails(tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb details not found")
        val ids =
            (details.genres.map { ShowTermKind.GENRE to it } + details.keywords.map { ShowTermKind.TAG to it })
                .map { (kind, name) -> upsert(show, kind, name, ShowTermSource.TMDB).id }
                .distinct()
        syncState.markSuccess(showId)
        return ShowTermsSyncResponse(showId, ids.size, query.getShowTerms(showId).count { it.active })
    }

    @Transactional
    fun syncFromTmdbIfLinked(showId: Long) {
        if (shows.findById(showId).orElse(null)?.tmdbId != null) {
            syncFromTmdb(showId)
        }
    }

    fun syncAllFromTmdb(limit: Int = 25): ShowTermsBatchSyncResponse {
        val resolved = limit.coerceIn(1, 1000)
        val candidates = syncState.candidates(resolved)
        var synced = 0
        var failed = 0
        candidates.forEach { candidate ->
            runCatching { transactions.execute { syncFromTmdb(candidate.showId) } }
                .onSuccess { synced++ }
                .onFailure { error ->
                    failed++
                    transactions.execute { syncState.markFailure(candidate.showId, error.message ?: error.javaClass.simpleName) }
                }
        }
        return ShowTermsBatchSyncResponse(resolved, candidates.size, candidates.size, synced, failed)
    }

    @Transactional
    fun addTerm(
        showId: Long,
        request: ShowTermCreateRequest,
    ): ShowTermDto {
        val term = upsert(requireShow(showId), ShowTermKind.valueOf(request.kind.name), request.name, ShowTermSource.USER)
        return query.getShowTerms(showId).first { it.id == term.id }
    }

    @Transactional
    fun updateShowVisibility(
        showId: Long,
        termId: Long,
        hidden: Boolean,
    ): ShowTermDto {
        requireShow(showId)
        if (assignments.updateVisibility(showId, termId, hidden) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Show term assignment not found")
        }
        return query.getShowTerms(showId).first { it.id == termId }
    }

    @Transactional
    fun updateGlobalVisibility(
        termId: Long,
        hidden: Boolean,
    ): ShowTermDto {
        val term = terms.findById(termId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Term not found") }
        terms.save(term.copy(hidden = hidden, updatedAt = Instant.now()))
        return query.findShowTerm(termId) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Term not found")
    }

    private fun requireShow(showId: Long): TvShow =
        shows.findById(showId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")
        }

    private fun upsert(
        show: TvShow,
        kind: ShowTermKind,
        rawName: String,
        source: ShowTermSource,
    ): ShowTerm {
        val name = rawName.trim().replace("\\s+".toRegex(), " ")
        if (name.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name deve ser preenchido")
        val normalized = name.lowercase()
        val existing = terms.findByKindAndNormalizedName(kind, normalized)
        var term =
            existing ?: terms.save(
                ShowTerm(name = name, normalizedName = normalized, slug = SlugTextUtil.normalize(name, 64), kind = kind, source = source),
            )
        if (term.hidden) term = terms.save(term.copy(hidden = false, updatedAt = Instant.now()))
        assignments.upsert(show.id, term.id, source)
        return term
    }
}
