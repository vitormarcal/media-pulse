package dev.marcal.mediapulse.server.api.ratings

import java.time.Instant

data class MediaRatingDto(
    val rating: Int,
    val updatedAt: Instant,
)

data class UpsertMediaRatingRequest(
    val rating: Int,
)
