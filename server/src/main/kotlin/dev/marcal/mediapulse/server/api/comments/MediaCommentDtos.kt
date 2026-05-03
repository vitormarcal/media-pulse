package dev.marcal.mediapulse.server.api.comments

import java.time.Instant

data class MediaCommentDto(
    val id: Long,
    val body: String,
    val commentedAt: Instant,
    val createdAt: Instant,
    val updatedAt: Instant,
    val edited: Boolean,
)

data class CreateMediaCommentRequest(
    val body: String,
    val commentedAt: Instant? = null,
)

data class UpdateMediaCommentRequest(
    val body: String,
    val commentedAt: Instant? = null,
)
