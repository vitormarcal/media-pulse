package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.ArtistMergeCandidateResponse
import dev.marcal.mediapulse.server.api.music.ArtistMergeCatalogResponse
import dev.marcal.mediapulse.server.api.music.ArtistMergePreviewRequest
import dev.marcal.mediapulse.server.api.music.ArtistMergePreviewResponse
import dev.marcal.mediapulse.server.api.music.ArtistMergeRequest
import dev.marcal.mediapulse.server.api.music.ArtistMergeResponse
import dev.marcal.mediapulse.server.api.music.DuplicateArtistReviewResponse
import dev.marcal.mediapulse.server.api.music.DuplicateArtistSuggestionResponse
import dev.marcal.mediapulse.server.repository.ArtistMergeRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class DuplicateArtistReviewService(
    private val repository: ArtistMergeRepository,
    private val artists: ArtistRepository,
) {
    fun catalog(
        query: String,
        limit: Int,
    ): ArtistMergeCatalogResponse {
        require(query.trim().length >= 2) { "q deve conter ao menos dois caracteres" }
        return ArtistMergeCatalogResponse(repository.findCandidates(repository.findCatalogIds(query, limit)))
    }

    fun suggestions(
        limit: Int,
        artist: String?,
    ): DuplicateArtistReviewResponse {
        val rows = repository.findSuggestions(limit, artist)
        val candidates = repository.findCandidates(rows.flatMap { listOf(it.leftId, it.rightId) }.distinct()).associateBy { it.artistId }
        return DuplicateArtistReviewResponse(
            rows.mapNotNull { row ->
                val choices = listOfNotNull(candidates[row.leftId], candidates[row.rightId])
                if (choices.size != 2) return@mapNotNull null
                val target = choices.maxWithOrNull(compareBy<ArtistMergeCandidateResponse>(::score).thenBy { -it.artistId })!!
                DuplicateArtistSuggestionResponse(
                    reason = "Nomes equivalentes após normalização",
                    confidence = "MEDIUM",
                    suggestedTargetArtistId = target.artistId,
                    candidates = choices.sortedByDescending(::score),
                )
            },
        )
    }

    fun preview(request: ArtistMergePreviewRequest): ArtistMergePreviewResponse {
        val candidates = resolve(request.targetArtistId, request.sourceArtistIds)
        return ArtistMergePreviewResponse(
            targetArtistId = request.targetArtistId,
            candidates = candidates,
            totalAlbums = candidates.sumOf { it.albumCount },
            totalTracks = candidates.sumOf { it.trackCount },
            totalPlaybacks = candidates.sumOf { it.playbackCount },
            warnings =
                buildList {
                    add(
                        "A operação é definitiva.",
                    )
                    add(
                        "Álbuns e faixas serão mantidos separados.",
                    )
                    add(
                        "Nomes só serão preservados quando marcados como alias.",
                    )
                    if (hasExternalConflict(candidates)) {
                        add("Existem identificadores externos divergentes; a mesclagem ficará bloqueada até a correção.")
                    }
                },
        )
    }

    @Transactional
    fun merge(request: ArtistMergeRequest): ArtistMergeResponse {
        val candidates = resolve(request.targetArtistId, request.sourceArtistIds)
        val allowed = candidates.mapTo(mutableSetOf()) { it.artistId }
        val selected =
            listOfNotNull(request.nameFromArtistId, request.imageFromArtistId, request.musicBrainzFromArtistId, request.ratingFromArtistId)
        if (selected.any { it !in allowed } ||
            request.preserveAliasArtistIds.any { it !in allowed } ||
            request.nameFromArtistId in request.preserveAliasArtistIds
        ) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "As escolhas devem pertencer à mesclagem e o nome canônico não pode ser seu próprio alias",
            )
        }
        if (hasExternalConflict(candidates)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Identificadores externos conflitantes impedem a mesclagem")
        }
        repository.lockArtists(allowed)
        val freshCandidates = resolve(request.targetArtistId, request.sourceArtistIds)
        if (hasExternalConflict(freshCandidates)) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Identificadores externos conflitantes impedem a mesclagem")
        }
        val byId = freshCandidates.associateBy { it.artistId }
        val selectedName = byId.getValue(request.nameFromArtistId).name
        val stats =
            repository.merge(
                ArtistMergeRepository.MergeCommand(
                    targetId = request.targetArtistId,
                    sourceIds = request.sourceArtistIds.distinct(),
                    nameId = request.nameFromArtistId,
                    imageId = request.imageFromArtistId,
                    musicBrainzId = request.musicBrainzFromArtistId,
                    ratingId = request.ratingFromArtistId,
                    aliasIds = request.preserveAliasArtistIds.distinct(),
                    spotifyId = freshCandidates.mapNotNull { it.spotifyId }.singleOrNull(),
                    musicBrainzValue = byId.getValue(request.musicBrainzFromArtistId).musicBrainzArtistId,
                    fingerprint = FingerprintUtil.artistFp(selectedName),
                ),
            )
        val target = artists.findById(request.targetArtistId).orElseThrow()
        return ArtistMergeResponse(
            artistId = target.id,
            mergedArtistIds = request.sourceArtistIds.distinct().sorted(),
            movedAlbums = stats.movedAlbums,
            movedTracks = stats.movedTracks,
            movedComments = stats.movedComments,
            mergedGenres = stats.mergedGenres,
            storedNameAliases = stats.storedAliases,
        )
    }

    private fun resolve(
        targetId: Long,
        sourceIds: List<Long>,
    ): List<ArtistMergeCandidateResponse> {
        require(sourceIds.isNotEmpty()) { "sourceArtistIds deve conter ao menos um artista" }
        require(targetId !in sourceIds) { "targetArtistId não pode aparecer em sourceArtistIds" }
        if (sourceIds.distinct().size != sourceIds.size) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "IDs de artistas duplicados")
        val result = repository.findCandidates(sourceIds + targetId)
        if (result.size !=
            sourceIds.size + 1
        ) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Um ou mais artistas não foram encontrados")
        }
        return result
    }

    private fun score(candidate: ArtistMergeCandidateResponse): Long =
        (if (candidate.musicBrainzArtistId != null) 1_000_000 else 0) +
            (if (candidate.spotifyId != null) 100_000 else 0) +
            (if (candidate.profileImageUrl != null) 10_000 else 0) +
            candidate.albumCount * 100 + candidate.trackCount * 10 + candidate.playbackCount

    private fun hasExternalConflict(candidates: List<ArtistMergeCandidateResponse>): Boolean =
        candidates.mapNotNull { it.spotifyId }.distinct().size > 1 ||
            candidates.mapNotNull { it.musicBrainzArtistId }.distinct().size > 1
}
