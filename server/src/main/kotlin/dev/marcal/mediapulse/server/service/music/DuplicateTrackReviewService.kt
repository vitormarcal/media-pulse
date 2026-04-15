package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.DuplicateTrackCandidateResponse
import dev.marcal.mediapulse.server.api.music.DuplicateTrackBatchMergeRequest
import dev.marcal.mediapulse.server.api.music.DuplicateTrackBatchMergeResponse
import dev.marcal.mediapulse.server.api.music.DuplicateTrackGroupResponse
import dev.marcal.mediapulse.server.api.music.DuplicateTrackIgnoreRequest
import dev.marcal.mediapulse.server.api.music.DuplicateTrackMergeRequest
import dev.marcal.mediapulse.server.api.music.DuplicateTrackMergeResponse
import dev.marcal.mediapulse.server.api.music.DuplicateTrackReviewPageResponse
import dev.marcal.mediapulse.server.repository.MusicDuplicateReviewRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.util.Base64

@Service
class DuplicateTrackReviewService(
    private val repository: MusicDuplicateReviewRepository,
) {
    data class ReviewCursor(
        val albumId: Long,
        val groupKey: String,
    )

    fun listGroups(
        limit: Int,
        cursor: String?,
        includeIgnored: Boolean,
        artistQuery: String?,
        albumQuery: String?,
    ): DuplicateTrackReviewPageResponse {
        val resolvedLimit = limit.coerceIn(1, 100)
        val decodedCursor = decodeCursor(cursor)
        val rows =
            repository.findDuplicateTrackCandidates(
                limit = resolvedLimit,
                cursorAlbumId = decodedCursor?.albumId,
                cursorGroupKey = decodedCursor?.groupKey,
                includeIgnored = includeIgnored,
                artistQuery = artistQuery,
                albumQuery = albumQuery,
            )

        val grouped =
            rows
                .groupBy { it.albumId to it.groupKey }
                .values
                .map(::toGroupResponse)

        val hasMore = grouped.size > resolvedLimit
        val items = grouped.take(resolvedLimit)
        val nextCursor = items.lastOrNull()?.let { encodeCursor(ReviewCursor(it.albumId, it.groupKey)) }

        return DuplicateTrackReviewPageResponse(
            items = items,
            nextCursor = if (hasMore) nextCursor else null,
        )
    }

    @Transactional
    fun setIgnored(request: DuplicateTrackIgnoreRequest) {
        repository.setIgnored(
            albumId = request.albumId,
            groupKey = request.groupKey,
            ignored = request.ignored,
        )
    }

    @Transactional
    fun merge(request: DuplicateTrackMergeRequest): DuplicateTrackMergeResponse {
        return mergeInternal(request)
    }

    @Transactional
    fun mergeBatch(request: DuplicateTrackBatchMergeRequest): DuplicateTrackBatchMergeResponse {
        require(request.merges.isNotEmpty()) { "merges deve conter ao menos um grupo" }

        val results = request.merges.map(::mergeInternal)
        return DuplicateTrackBatchMergeResponse(
            processedGroups = results.size,
            results = results,
        )
    }

    private fun mergeInternal(request: DuplicateTrackMergeRequest): DuplicateTrackMergeResponse {
        require(request.sourceTrackIds.isNotEmpty()) { "sourceTrackIds deve conter ao menos uma faixa" }
        require(request.sourceTrackIds.none { it == request.targetTrackId }) { "targetTrackId não pode aparecer em sourceTrackIds" }

        repository.lockAlbum(request.albumId)

        val candidates = repository.findGroupCandidates(request.albumId, request.groupKey)
        if (candidates.size < 2) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Grupo não encontrado ou não possui duplicatas suficientes")
        }

        val allowedTrackIds = candidates.map { it.trackId }.toSet()
        if (request.targetTrackId !in allowedTrackIds) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "targetTrackId não pertence ao grupo selecionado")
        }

        val invalidSourceIds = request.sourceTrackIds.filterNot(allowedTrackIds::contains)
        if (invalidSourceIds.isNotEmpty()) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "sourceTrackIds inválidos para o grupo: ${invalidSourceIds.joinToString(", ")}",
            )
        }

        val mergeStats =
            repository.mergeTracks(
                targetTrackId = request.targetTrackId,
                sourceTrackIds = request.sourceTrackIds.distinct(),
            )

        repository.clearIgnored(
            albumId = request.albumId,
            groupKey = request.groupKey,
        )

        return DuplicateTrackMergeResponse(
            albumId = request.albumId,
            groupKey = request.groupKey,
            targetTrackId = request.targetTrackId,
            mergedTrackIds = request.sourceTrackIds.distinct().sorted(),
            deletedDuplicatePlaybacks = mergeStats.deletedDuplicatePlaybacks,
            movedPlaybacks = mergeStats.movedPlaybacks,
            linkedExternalIdentifiers = mergeStats.linkedExternalIdentifiers,
            migratedAlbumLinks = mergeStats.migratedAlbumLinks,
        )
    }

    private fun toGroupResponse(rows: List<MusicDuplicateReviewRepository.DuplicateTrackCandidateRow>): DuplicateTrackGroupResponse {
        val sortedCandidates =
            rows.sortedWith(
                compareByDescending<MusicDuplicateReviewRepository.DuplicateTrackCandidateRow> {
                    candidateScore(it)
                }.thenBy { it.trackId },
            )
        val target = sortedCandidates.first()
        val confidence = confidenceFor(sortedCandidates)
        val suggestionReason = suggestionReasonFor(sortedCandidates)

        return DuplicateTrackGroupResponse(
            albumId = target.albumId,
            albumTitle = target.albumTitle,
            albumYear = target.albumYear,
            albumCoverUrl = target.albumCoverUrl,
            artistId = target.artistId,
            artistName = target.artistName,
            groupKey = target.groupKey,
            normalizedTitle = target.normalizedTitle,
            ignored = target.ignored,
            confidence = confidence,
            suggestionReason = suggestionReason,
            suggestedTargetTrackId = target.trackId,
            candidates =
                sortedCandidates.map {
                    DuplicateTrackCandidateResponse(
                        trackId = it.trackId,
                        title = it.title,
                        durationMs = it.durationMs,
                        discNumber = it.discNumber,
                        trackNumber = it.trackNumber,
                        playbackCount = it.playbackCount,
                        lastPlayed = it.lastPlayed,
                        hasMusicBrainz = it.hasMusicBrainz,
                        hasSpotify = it.hasSpotify,
                        externalIdentifiers = it.externalIdentifiers,
                    )
                },
        )
    }

    private fun candidateScore(candidate: MusicDuplicateReviewRepository.DuplicateTrackCandidateRow): Int {
        var score = 0
        if (candidate.hasMusicBrainz) score += 1_000
        score += candidate.externalIdentifiers.size * 100
        score += candidate.playbackCount.coerceAtMost(99).toInt() * 10
        if (candidate.trackNumber != null) score += 5
        if (candidate.discNumber != null) score += 5
        if (candidate.hasSpotify) score += 3
        return score
    }

    private fun confidenceFor(rows: List<MusicDuplicateReviewRepository.DuplicateTrackCandidateRow>): String {
        val winner = rows.maxByOrNull(::candidateScore) ?: return "low"
        val runnerUp = rows.filterNot { it.trackId == winner.trackId }.maxOfOrNull(::candidateScore) ?: 0
        val gap = candidateScore(winner) - runnerUp
        return when {
            winner.hasMusicBrainz && gap >= 500 -> "high"
            gap >= 150 -> "medium"
            else -> "low"
        }
    }

    private fun suggestionReasonFor(rows: List<MusicDuplicateReviewRepository.DuplicateTrackCandidateRow>): String {
        val winner = rows.maxByOrNull(::candidateScore) ?: return "Faixa sugerida pelo conjunto de sinais disponíveis."
        return when {
            winner.hasMusicBrainz -> "A faixa sugerida já está vinculada ao MusicBrainz e tende a ser a referência mais estável."
            winner.externalIdentifiers.size > 1 -> "A faixa sugerida concentra mais identificadores externos que as demais."
            winner.playbackCount > 0 -> "A faixa sugerida já carrega mais playbacks associados neste álbum."
            else -> "A faixa sugerida preserva mais metadados posicionais para o álbum."
        }
    }

    private fun encodeCursor(cursor: ReviewCursor): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString("${cursor.albumId}|${cursor.groupKey}".toByteArray())

    private fun decodeCursor(raw: String?): ReviewCursor? {
        if (raw.isNullOrBlank()) return null
        return runCatching {
            val decoded = String(Base64.getUrlDecoder().decode(raw))
            val parts = decoded.split("|", limit = 2)
            ReviewCursor(
                albumId = parts[0].toLong(),
                groupKey = parts[1],
            )
        }.getOrNull()
    }
}
