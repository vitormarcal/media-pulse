package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowEpisodesRefreshResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import dev.marcal.mediapulse.server.util.TxUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@Service
class ShowEpisodesRefreshService(
    private val shows: TvShowRepository,
    private val episodes: TvEpisodeRepository,
    private val tmdb: TmdbApiClient,
    private val tx: TxUtil,
) {
    fun refresh(showId: Long): ShowEpisodesRefreshResponse {
        val show =
            shows.findById(showId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Série não encontrada")
            }
        val tmdbId =
            show.tmdbId?.takeIf { it.isNotBlank() }
                ?: throw ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Vincule a série ao TMDb em Enriquecer dados antes de atualizar episódios",
                )
        val details =
            tmdb.fetchShowDetails(tmdbId)
                ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar a série no TMDb")
        // Fetch every season before writing so a provider failure never leaves a partial import.
        val seasons =
            details.seasons.mapNotNull { it.seasonNumber }.filter { it > 0 }.distinct().sorted().map { number ->
                val season =
                    tmdb.fetchShowSeasonDetails(tmdbId, number)
                        ?: throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar a temporada $number no TMDb")
                number to season
            }
        return tx.inTx {
            val lockedShow =
                shows.findByIdForUpdate(showId)
                    ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Série não encontrada")
            if (lockedShow.tmdbId != tmdbId) {
                throw ResponseStatusException(HttpStatus.CONFLICT, "O vínculo TMDb mudou. Atualize a página e tente novamente")
            }
            var addedSeasons = 0
            var addedEpisodes = 0
            seasons.forEach { (number, season) ->
                val seasonExisted = episodes.findByShowIdAndSeasonNumberOrderByEpisodeNumberAscIdAsc(showId, number).isNotEmpty()
                var seasonAddedEpisodes = 0
                season.episodes.forEach episodeLoop@{ candidate ->
                    val episodeNumber = candidate.episodeNumber?.takeIf { it > 0 } ?: return@episodeLoop
                    val title = candidate.title?.trim()?.takeIf { it.isNotEmpty() } ?: "Episódio $episodeNumber"
                    val fingerprint = FingerprintUtil.tvEpisodeFp(showId, number, episodeNumber, title)
                    val linked = candidate.tmdbId?.let(episodes::findByTmdbId)
                    if (linked != null && linked.showId != showId) {
                        throw ResponseStatusException(HttpStatus.CONFLICT, "Episódio TMDb já vinculado a outra série")
                    }
                    val existing =
                        linked
                            ?: episodes.findByShowIdAndSeasonNumberAndEpisodeNumber(showId, number, episodeNumber)
                            ?: episodes.findByFingerprint(fingerprint)
                    if (existing != null) return@episodeLoop
                    episodes.save(
                        TvEpisode(
                            showId = showId,
                            title = title,
                            seasonNumber = number,
                            seasonTitle = season.title,
                            episodeNumber = episodeNumber,
                            summary = candidate.overview,
                            durationMs = candidate.runtimeMinutes?.let { it * 60 * 1000 },
                            originallyAvailableAt = candidate.airDate?.let { runCatching { LocalDate.parse(it) }.getOrNull() },
                            fingerprint = fingerprint,
                            tmdbId = candidate.tmdbId,
                        ),
                    )
                    seasonAddedEpisodes++
                }
                addedEpisodes += seasonAddedEpisodes
                if (!seasonExisted && seasonAddedEpisodes > 0) addedSeasons++
            }
            ShowEpisodesRefreshResponse(showId, addedSeasons, addedEpisodes)
        }
    }
}
