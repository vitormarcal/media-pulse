package dev.marcal.mediapulse.server.controller.ratings

import dev.marcal.mediapulse.server.api.ratings.MediaRatingDto
import dev.marcal.mediapulse.server.api.ratings.UpsertMediaRatingRequest
import dev.marcal.mediapulse.server.service.rating.MediaRatingService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/ratings")
class MediaRatingsController(
    private val mediaRatingService: MediaRatingService,
) {
    @PostMapping("/{mediaType}/{entityId}")
    fun upsert(
        @PathVariable mediaType: String,
        @PathVariable entityId: Long,
        @RequestBody request: UpsertMediaRatingRequest,
    ): MediaRatingDto = mediaRatingService.upsert(mediaType, entityId, request)

    @DeleteMapping("/{mediaType}/{entityId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    fun clear(
        @PathVariable mediaType: String,
        @PathVariable entityId: Long,
    ) {
        mediaRatingService.clear(mediaType, entityId)
    }
}
