package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.AlbumContextApplyRequest
import dev.marcal.mediapulse.server.service.music.AlbumContextService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/music/albums/{albumId}/context")
class AlbumContextController(
    private val service: AlbumContextService,
) {
    @PostMapping("/preview")
    suspend fun preview(
        @PathVariable albumId: Long,
    ) = service.preview(albumId)

    @PostMapping
    fun apply(
        @PathVariable albumId: Long,
        @RequestBody request: AlbumContextApplyRequest,
    ) = service.apply(albumId, request.token)
}
