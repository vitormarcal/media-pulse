package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.AlbumArtistDto
import dev.marcal.mediapulse.server.api.music.AlbumMergeCandidateResponse
import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException

object AlbumCreditMergePlanner {
    data class Plan(
        val fromAlbumId: Long,
        val artists: List<AlbumArtistDto>,
        val requiresChoice: Boolean,
    )

    fun plan(
        candidates: List<AlbumMergeCandidateResponse>,
        targetId: Long,
        selectedId: Long?,
    ): Plan {
        val suggested =
            candidates
                .sortedWith(
                    compareByDescending<AlbumMergeCandidateResponse> { candidate ->
                        candidate.artists.maxOfOrNull { priority(it.source) }
                            ?: 0
                    }.thenByDescending {
                        it.artists
                            .map { artist -> artist.artistId }
                            .distinct()
                            .size
                    }.thenBy { if (it.albumId == targetId) 0 else 1 }
                        .thenBy { it.albumId },
                ).first()
        val chosen =
            candidates.firstOrNull { it.albumId == (selectedId ?: suggested.albumId) }
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Os créditos devem vir de um dos álbuns selecionados")
        val signatures =
            candidates
                .map { candidate ->
                    candidate.artists.map { Triple(it.artistId, it.creditedName, it.joinPhrase) }
                }.distinct()
        return Plan(chosen.albumId, chosen.artists, signatures.size > 1)
    }

    private fun priority(source: AlbumArtistSource): Int =
        when (source) {
            AlbumArtistSource.LEGACY -> 0
            AlbumArtistSource.SPOTIFY -> 1
            AlbumArtistSource.MUSICBRAINZ_CONFIRMED -> 2
        }
}
