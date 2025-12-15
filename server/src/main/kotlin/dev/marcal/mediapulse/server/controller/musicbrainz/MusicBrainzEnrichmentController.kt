package dev.marcal.mediapulse.server.controller.musicbrainz

import dev.marcal.mediapulse.server.service.musicbrainz.MusicBrainzAlbumGenreEnrichmentService
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.ResponseStatus
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/musicbrainz")
class MusicBrainzEnrichmentController(
    private val service: MusicBrainzAlbumGenreEnrichmentService,
) {
    @PostMapping("/enrich-album-genres")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun enrich(
        @RequestParam(defaultValue = "200") limit: Int,
    ) {
        service.enrichBatchAsync(limit)
    }
}
