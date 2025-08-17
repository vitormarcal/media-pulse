package dev.marcal.mediapulse.server.controller.playbacksummary

import dev.marcal.mediapulse.server.controller.dto.ApiResult
import dev.marcal.mediapulse.server.controller.playbacksummary.dto.TrackPlaybackSummary
import dev.marcal.mediapulse.server.repository.MusicAggregationRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/playbacks")
class PlaybackSummaryController(
    private val musicAggregationRepository: MusicAggregationRepository,
) {
    @GetMapping("/top")
    fun getTopTracks(
        @RequestParam("period", required = false, defaultValue = "month") period: String,
        @RequestParam("limit", required = false, defaultValue = "10") limit: Int,
    ): ApiResult<List<TrackPlaybackSummary>> {
        val (start, end) = getPeriodRange(period)
        val data = musicAggregationRepository.getPlaybackSummaryByPeriod(start, end, limit)
        return ApiResult(data = data)
    }

    @GetMapping("/top/{year}")
    fun getTopTracksByYear(
        @PathVariable year: Int,
        @RequestParam("limit", required = false, defaultValue = "10") limit: Int,
    ): ApiResult<List<TrackPlaybackSummary>> {
        val start = Instant.parse("$year-01-01T00:00:00Z")
        val end = Instant.parse("${year + 1}-01-01T00:00:00Z")
        val data = musicAggregationRepository.getPlaybackSummaryByPeriod(start, end, limit)
        return ApiResult(data = data)
    }

    @GetMapping("/recent")
    fun getRecentTracks(
        @RequestParam("period", required = false, defaultValue = "day") period: String,
        @RequestParam("limit", required = false, defaultValue = "10") limit: Int,
    ): ApiResult<List<TrackPlaybackSummary>> {
        val (start, end) = getPeriodRange(period)
        val data = musicAggregationRepository.getPlaybackSummaryByPeriod(start, end, limit)
        return ApiResult(data = data)
    }

    private fun getPeriodRange(period: String): Pair<Instant, Instant> {
        val now = Instant.now()
        return when (period) {
            "month" -> Pair(now.minusSeconds(60 * 60 * 24 * 30), now)
            "week" -> Pair(now.minusSeconds(60 * 60 * 24 * 7), now)
            "year" -> Pair(now.minusSeconds(60 * 60 * 24 * 365), now)
            "decade" -> Pair(now.minusSeconds(60 * 60 * 24 * 365 * 10), now)
            "day" -> Pair(now.minusSeconds(60 * 60 * 24), now)
            "hour" -> Pair(now.minusSeconds(60 * 60), now)
            else -> Pair(now.minusSeconds(60 * 60 * 24 * 30), now)
        }
    }
}
