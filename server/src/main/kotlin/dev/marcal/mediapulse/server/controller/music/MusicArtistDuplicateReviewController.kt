package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.ArtistMergeCatalogResponse
import dev.marcal.mediapulse.server.api.music.ArtistMergePreviewRequest
import dev.marcal.mediapulse.server.api.music.ArtistMergePreviewResponse
import dev.marcal.mediapulse.server.api.music.ArtistMergeRequest
import dev.marcal.mediapulse.server.api.music.ArtistMergeResponse
import dev.marcal.mediapulse.server.api.music.DuplicateArtistReviewResponse
import dev.marcal.mediapulse.server.service.music.DuplicateArtistReviewService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/music/admin/artist-duplicates")
class MusicArtistDuplicateReviewController(
    private val service: DuplicateArtistReviewService,
) {
    @GetMapping
    fun suggestions(
        @RequestParam(defaultValue = "50") limit: Int,
        @RequestParam(required = false) artist: String?,
    ): DuplicateArtistReviewResponse = service.suggestions(limit, artist)

    @GetMapping("/catalog")
    fun catalog(
        @RequestParam q: String,
        @RequestParam(defaultValue = "100") limit: Int,
    ): ArtistMergeCatalogResponse = service.catalog(q, limit)

    @PostMapping("/preview")
    fun preview(
        @RequestBody request: ArtistMergePreviewRequest,
    ): ArtistMergePreviewResponse = service.preview(request)

    @PostMapping("/merge")
    fun merge(
        @RequestBody request: ArtistMergeRequest,
    ): ArtistMergeResponse = service.merge(request)
}
