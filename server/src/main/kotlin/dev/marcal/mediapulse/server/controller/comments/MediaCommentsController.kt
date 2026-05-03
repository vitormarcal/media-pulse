package dev.marcal.mediapulse.server.controller.comments

import dev.marcal.mediapulse.server.api.comments.CreateMediaCommentRequest
import dev.marcal.mediapulse.server.api.comments.MediaCommentDto
import dev.marcal.mediapulse.server.api.comments.UpdateMediaCommentRequest
import dev.marcal.mediapulse.server.service.comment.MediaCommentService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/comments")
class MediaCommentsController(
    private val mediaCommentService: MediaCommentService,
) {
    @PostMapping("/{mediaType}/{entityId}")
    fun create(
        @PathVariable mediaType: String,
        @PathVariable entityId: Long,
        @RequestBody request: CreateMediaCommentRequest,
    ): MediaCommentDto = mediaCommentService.create(mediaType, entityId, request)

    @PostMapping("/{commentId}/edit")
    fun update(
        @PathVariable commentId: Long,
        @RequestBody request: UpdateMediaCommentRequest,
    ): MediaCommentDto = mediaCommentService.update(commentId, request)
}
