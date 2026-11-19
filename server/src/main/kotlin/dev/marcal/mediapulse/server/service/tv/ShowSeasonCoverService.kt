package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionRequest
import dev.marcal.mediapulse.server.repository.ShowSeasonCoverRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.service.image.TmdbImageSelectionService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ShowSeasonCoverService(
    private val shows: TvShowRepository,
    private val episodes: TvEpisodeRepository,
    private val covers: ShowSeasonCoverRepository,
    private val selection: TmdbImageSelectionService,
) {
    fun candidates(
        showId: Long,
        seasonNumber: Int,
    ) = selection.candidates(subject(showId, seasonNumber))

    fun save(
        showId: Long,
        seasonNumber: Int,
        request: TmdbImageSelectionRequest,
    ): dev.marcal.mediapulse.server.api.image.TmdbImageSelectionResponse {
        if (request.ids.size != 1) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Escolha uma única capa.")
        return selection.save(subject(showId, seasonNumber), request) { url -> covers.save(showId, seasonNumber, url) }
    }

    fun remove(
        showId: Long,
        seasonNumber: Int,
    ) {
        subject(showId, seasonNumber)
        covers.remove(showId, seasonNumber)
    }

    private fun subject(
        showId: Long,
        seasonNumber: Int,
    ): TmdbImageSelectionService.Subject {
        if (seasonNumber < 0) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Temporada inválida.")
        val show = shows.findById(showId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Série não encontrada.") }
        if (episodes.findByShowIdAndSeasonNumberOrderByEpisodeNumberAscIdAsc(showId, seasonNumber).isEmpty()) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Temporada não encontrada.")
        }
        return TmdbImageSelectionService.Subject(
            TmdbImageSelectionService.Kind.SEASON,
            showId,
            show.originalTitle,
            show.tmdbId,
            seasonNumber,
        )
    }
}
