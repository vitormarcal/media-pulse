package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.DuplicateTrackBatchMergeRequest
import dev.marcal.mediapulse.server.api.music.DuplicateTrackBatchMergeResponse
import dev.marcal.mediapulse.server.api.music.DuplicateTrackIgnoreRequest
import dev.marcal.mediapulse.server.api.music.DuplicateTrackMergeRequest
import dev.marcal.mediapulse.server.api.music.DuplicateTrackMergeResponse
import dev.marcal.mediapulse.server.api.music.DuplicateTrackReviewPageResponse
import dev.marcal.mediapulse.server.service.music.DuplicateTrackReviewService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/music/admin/track-duplicates")
class MusicDuplicateReviewController(
    private val service: DuplicateTrackReviewService,
) {
    @GetMapping
    fun list(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
        @RequestParam(defaultValue = "false") includeIgnored: Boolean,
        @RequestParam(required = false) artist: String?,
        @RequestParam(required = false) album: String?,
    ): DuplicateTrackReviewPageResponse =
        service.listGroups(
            limit = limit,
            cursor = cursor,
            includeIgnored = includeIgnored,
            artistQuery = artist,
            albumQuery = album,
        )

    @PostMapping("/ignore")
    fun ignore(
        @RequestBody request: DuplicateTrackIgnoreRequest,
    ) {
        service.setIgnored(request)
    }

    @PostMapping("/merge")
    fun merge(
        @RequestBody request: DuplicateTrackMergeRequest,
    ): DuplicateTrackMergeResponse = service.merge(request)

    @PostMapping("/merge-batch")
    fun mergeBatch(
        @RequestBody request: DuplicateTrackBatchMergeRequest,
    ): DuplicateTrackBatchMergeResponse = service.mergeBatch(request)
}
