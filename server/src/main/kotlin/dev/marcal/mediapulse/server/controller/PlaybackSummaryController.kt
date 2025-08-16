package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.controller.dto.ApiResult
import dev.marcal.mediapulse.server.controller.dto.TrackPlaybackSummary
import dev.marcal.mediapulse.server.repository.PlaybackAggregationRepository
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Instant

@RestController
@RequestMapping("/playbacks/summary")
class PlaybackSummaryController(
    private val playbackAggregationRepository: PlaybackAggregationRepository,
) {
    @GetMapping
    fun getPlaybacksByPeriod(
        @RequestParam("start_date", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        startDate: Instant?,
        @RequestParam("end_date", required = false)
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
        endDate: Instant?,
    ): ApiResult<List<TrackPlaybackSummary>> {
        val now = Instant.now()
        val start = startDate ?: now.minusSeconds(60 * 60 * 24 * 30)
        val end = endDate ?: now
        val data = playbackAggregationRepository.getPlaybackSummaryByPeriod(start, end)
        return ApiResult(data = data)
    }
}
