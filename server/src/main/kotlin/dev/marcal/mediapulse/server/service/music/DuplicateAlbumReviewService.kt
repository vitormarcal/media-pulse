package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.AlbumMergeArtistResponse
import dev.marcal.mediapulse.server.api.music.AlbumMergeCandidateResponse
import dev.marcal.mediapulse.server.api.music.AlbumMergeCatalogResponse
import dev.marcal.mediapulse.server.api.music.AlbumMergePreviewRequest
import dev.marcal.mediapulse.server.api.music.AlbumMergePreviewResponse
import dev.marcal.mediapulse.server.api.music.AlbumMergeRequest
import dev.marcal.mediapulse.server.api.music.AlbumMergeResponse
import dev.marcal.mediapulse.server.api.music.AlbumTrackOrderPreviewResponse
import dev.marcal.mediapulse.server.api.music.DuplicateAlbumReviewResponse
import dev.marcal.mediapulse.server.api.music.DuplicateAlbumSuggestionResponse
import dev.marcal.mediapulse.server.repository.AlbumMergeRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import dev.marcal.mediapulse.server.util.TitleKeyUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class DuplicateAlbumReviewService(
    private val repository: AlbumMergeRepository,
    private val albumRepository: AlbumRepository,
) {
    fun catalog(
        query: String,
        limit: Int,
    ): AlbumMergeCatalogResponse {
        require(query.trim().length >= 2) { "q deve conter ao menos dois caracteres" }
        val rows = repository.findCandidates(repository.findCatalogAlbumIds(query, limit))
        return AlbumMergeCatalogResponse(
            artists =
                rows.groupBy { it.first }.map { (artist, albums) ->
                    AlbumMergeArtistResponse(
                        artistId = artist.first,
                        artistName = artist.second,
                        albums = albums.map { it.second },
                    )
                },
        )
    }

    fun suggestions(
        limit: Int,
        artistQuery: String?,
        albumQuery: String?,
    ): DuplicateAlbumReviewResponse {
        val rows = repository.findSuggestions(limit, artistQuery, albumQuery)
        val candidates =
            repository.findCandidates(rows.flatMap { listOf(it.leftAlbumId, it.rightAlbumId) }.distinct()).associate {
                it.second.albumId to
                    it
            }
        return DuplicateAlbumReviewResponse(
            rows.mapNotNull { row ->
                val left = candidates[row.leftAlbumId] ?: return@mapNotNull null
                val right = candidates[row.rightAlbumId] ?: return@mapNotNull null
                val choices = listOf(left.second, right.second)
                val candidateComparator =
                    compareBy<AlbumMergeCandidateResponse>(::score)
                        .thenBy { -it.albumId }
                val target =
                    choices.maxWithOrNull(candidateComparator)
                        ?: return@mapNotNull null
                val overlap =
                    if (row.smallerTrackCount == 0L) {
                        0
                    } else {
                        (row.sharedTracks * 100 / row.smallerTrackCount).toInt()
                    }
                DuplicateAlbumSuggestionResponse(
                    artistId = left.first.first,
                    artistName = left.first.second,
                    reason =
                        if (row.titleMatch) {
                            "Títulos equivalentes após remover qualificadores de edição"
                        } else {
                            "$overlap% das faixas do menor álbum aparecem no outro"
                        },
                    confidence = if (row.titleMatch && overlap >= 50) "HIGH" else "MEDIUM",
                    suggestedTargetAlbumId = target.albumId,
                    candidates = choices.sortedByDescending(::score),
                )
            },
        )
    }

    fun preview(request: AlbumMergePreviewRequest): AlbumMergePreviewResponse {
        val resolved = resolve(request.targetAlbumId, request.sourceAlbumIds)
        validateSelectedAlbum(request.trackOrderFromAlbumId, resolved.candidates)
        val target = resolved.candidates.first { it.albumId == request.targetAlbumId }
        val trackOrder = planTrackOrder(resolved.candidates, request.trackOrderFromAlbumId)
        return AlbumMergePreviewResponse(
            artistId = resolved.artistId,
            artistName = resolved.artistName,
            targetAlbumId = target.albumId,
            candidates = resolved.candidates,
            totalTracks = resolved.candidates.sumOf { it.trackCount },
            totalPlaybacks = resolved.candidates.sumOf { it.playbackCount },
            trackOrder = trackOrder.toResponse(request.trackOrderFromAlbumId),
            warnings =
                buildList {
                    add("A operação é definitiva.")
                    add("Faixas com títulos iguais serão mantidas separadas.")
                    if (trackOrder.conflictedTrackCount > 0) {
                        add("${trackOrder.conflictedTrackCount} faixa(s) adicional(is) perderão a posição por conflito.")
                    }
                },
        )
    }

    @Transactional
    fun merge(request: AlbumMergeRequest): AlbumMergeResponse {
        val resolved = resolve(request.targetAlbumId, request.sourceAlbumIds)
        val allowed = resolved.candidates.associateBy { it.albumId }
        val selectedIds =
            listOfNotNull(
                request.titleFromAlbumId,
                request.coverFromAlbumId,
                request.yearFromAlbumId,
                request.ratingFromAlbumId,
                request.trackOrderFromAlbumId,
            )
        if (selectedIds.any { it !in allowed }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Os campos escolhidos devem vir de um dos álbuns da mesclagem")
        }

        repository.lockAlbums(allowed.keys)
        val fresh = resolve(request.targetAlbumId, request.sourceAlbumIds)
        val freshById = fresh.candidates.associateBy { it.albumId }
        val target =
            albumRepository.findById(request.targetAlbumId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "álbum principal não encontrado")
            }
        val titleChoice = freshById.getValue(request.titleFromAlbumId)
        val coverChoice = freshById.getValue(request.coverFromAlbumId)
        val yearChoice = freshById.getValue(request.yearFromAlbumId)
        val originalLinks = repository.findTrackLinks(freshById.keys)
        val trackOrder = AlbumTrackOrderPlanner.plan(originalLinks, request.trackOrderFromAlbumId)
        val targetTrackIds = originalLinks.filter { it.albumId == target.id }.mapTo(mutableSetOf()) { it.trackId }
        val migratedTrackLinks = trackOrder.links.count { it.trackId !in targetTrackIds }
        val stats =
            repository.merge(
                target.id,
                request.sourceAlbumIds.distinct(),
                fresh.artistId,
                request.ratingFromAlbumId,
                trackOrder.links,
                migratedTrackLinks,
            )
        val titleKey = TitleKeyUtil.albumTitleKey(titleChoice.title).ifBlank { "unknown" }
        albumRepository.save(
            target.copy(
                title = titleChoice.title,
                titleKey = titleKey,
                year = yearChoice.year,
                coverUrl = coverChoice.coverUrl,
                fingerprint = FingerprintUtil.albumFp(titleKey, target.artistId),
                updatedAt = Instant.now(),
            ),
        )
        return AlbumMergeResponse(
            albumId = target.id,
            mergedAlbumIds = request.sourceAlbumIds.distinct().sorted(),
            movedPlaybacks = stats.movedPlaybacks,
            migratedTrackLinks = stats.migratedTrackLinks,
            linkedExternalIdentifiers = stats.linkedExternalIdentifiers,
            storedTitleAliases = stats.storedTitleAliases,
        )
    }

    private fun resolve(
        targetId: Long,
        sourceIds: List<Long>,
    ): ResolvedMerge {
        require(sourceIds.isNotEmpty()) { "sourceAlbumIds deve conter ao menos um álbum" }
        require(targetId !in sourceIds) { "targetAlbumId não pode aparecer em sourceAlbumIds" }
        val ids = (sourceIds + targetId).distinct()
        if (ids.size != sourceIds.distinct().size + 1) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "IDs de álbuns duplicados")
        val rows = repository.findCandidates(ids)
        if (rows.size != ids.size) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Um ou mais álbuns não foram encontrados")
        val artists = rows.map { it.first }.distinctBy { it.first }
        if (artists.size != 1) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Só é possível mesclar álbuns do mesmo artista")
        return ResolvedMerge(artists.single().first, artists.single().second, rows.map { it.second })
    }

    private fun score(candidate: AlbumMergeCandidateResponse): Long =
        (candidate.musicBrainzReleaseGroupId?.let { 1_000_000L } ?: 0L) +
            candidate.musicBrainzReleaseIds.size * 100_000L +
            candidate.spotifyIds.size * 10_000L +
            (if (candidate.coverUrl != null) 1_000L else 0L) +
            candidate.trackCount * 10L + candidate.playbackCount

    private fun planTrackOrder(
        candidates: List<AlbumMergeCandidateResponse>,
        trackOrderFromAlbumId: Long,
    ) = AlbumTrackOrderPlanner.plan(repository.findTrackLinks(candidates.map { it.albumId }), trackOrderFromAlbumId)

    private fun validateSelectedAlbum(
        selectedAlbumId: Long,
        candidates: List<AlbumMergeCandidateResponse>,
    ) {
        if (candidates.none { it.albumId == selectedAlbumId }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "A ordem das faixas deve vir de um dos álbuns da mesclagem")
        }
    }

    private fun AlbumTrackOrderPlanner.Plan.toResponse(fromAlbumId: Long) =
        AlbumTrackOrderPreviewResponse(
            fromAlbumId = fromAlbumId,
            positionedTrackCount = positionedTrackCount,
            unpositionedTrackCount = unpositionedTrackCount,
            conflictedTrackCount = conflictedTrackCount,
        )

    private data class ResolvedMerge(
        val artistId: Long,
        val artistName: String,
        val candidates: List<AlbumMergeCandidateResponse>,
    )
}
