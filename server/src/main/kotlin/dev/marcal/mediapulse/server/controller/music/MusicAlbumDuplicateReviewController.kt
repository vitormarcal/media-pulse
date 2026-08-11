package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.AlbumMergeCatalogResponse
import dev.marcal.mediapulse.server.api.music.AlbumMergePreviewRequest
import dev.marcal.mediapulse.server.api.music.AlbumMergePreviewResponse
import dev.marcal.mediapulse.server.api.music.AlbumMergeRequest
import dev.marcal.mediapulse.server.api.music.AlbumMergeResponse
import dev.marcal.mediapulse.server.api.music.DuplicateAlbumReviewResponse
import dev.marcal.mediapulse.server.service.music.DuplicateAlbumReviewService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/music/admin/album-duplicates")
class MusicAlbumDuplicateReviewController(
    private val service: DuplicateAlbumReviewService,
) {
    @GetMapping("/catalog")
    fun catalog(
        @RequestParam q: String,
        @RequestParam(defaultValue = "100") limit: Int,
    ): AlbumMergeCatalogResponse = service.catalog(q, limit)

    @GetMapping
    fun suggestions(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) artist: String?,
        @RequestParam(required = false) album: String?,
    ): DuplicateAlbumReviewResponse = service.suggestions(limit, artist, album)

    @PostMapping("/preview")
    fun preview(
        @RequestBody request: AlbumMergePreviewRequest,
    ): AlbumMergePreviewResponse = service.preview(request)

    @PostMapping("/merge")
    fun merge(
        @RequestBody request: AlbumMergeRequest,
    ): AlbumMergeResponse = service.merge(request)
}
