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
    fun enrichBatch(
        @RequestParam(defaultValue = "200") limit: Int,
    ) {
        service.enrichBatchAsync(limit)
    }

    @PostMapping("/enrich-album-genres/drain")
    @ResponseStatus(HttpStatus.ACCEPTED)
    fun drain(
        @RequestParam(defaultValue = "200") batchSize: Int,
        @RequestParam(defaultValue = "50000") maxTotal: Int,
    ) {
        service.enrichAllAsync(
            batchSize = batchSize,
            maxTotal = maxTotal,
        )
    }
}
