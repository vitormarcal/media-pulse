package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionRequest
import dev.marcal.mediapulse.server.service.tv.ShowImageSelectionService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shows/{showId}/images")
class ShowImageSelectionController(
    private val service: ShowImageSelectionService,
) {
    @GetMapping("/candidates")
    fun candidates(
        @PathVariable showId: Long,
    ) = service.candidates(showId)

    @PostMapping("/lookup")
    fun lookup(
        @PathVariable showId: Long,
        @RequestBody request: TmdbImageSelectionRequest,
    ) = service.lookup(showId, request)

    @PostMapping("/selection")
    fun save(
        @PathVariable showId: Long,
        @RequestBody request: TmdbImageSelectionRequest,
    ) = service.save(showId, request)
}
