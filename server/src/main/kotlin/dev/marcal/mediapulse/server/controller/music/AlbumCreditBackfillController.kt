package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillPreviewRequest
import dev.marcal.mediapulse.server.service.music.AlbumCreditBackfillService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.util.UUID

@RestController
@RequestMapping("/api/admin/music/album-artist-backfills")
class AlbumCreditBackfillController(
    private val service: AlbumCreditBackfillService,
) {
    @GetMapping
    fun recent() = service.recent()

    @PostMapping("/preview")
    suspend fun preview(
        @RequestBody request: AlbumCreditBackfillPreviewRequest,
    ) = service.preview(request)

    @GetMapping("/{batchId}")
    fun get(
        @PathVariable batchId: UUID,
    ) = service.get(batchId)

    @PostMapping("/{batchId}/apply")
    fun apply(
        @PathVariable batchId: UUID,
    ) = service.apply(batchId)

    @PostMapping("/{batchId}/undo")
    fun undo(
        @PathVariable batchId: UUID,
    ) = service.undo(batchId)
}
