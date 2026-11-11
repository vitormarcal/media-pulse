package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.shows.ShowImageReferenceRequest
import dev.marcal.mediapulse.server.service.tv.ShowImageManagementService
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shows/{showId}/images")
class ShowImageManagementController(
    private val service: ShowImageManagementService,
) {
    @PostMapping("/primary")
    fun primary(
        @PathVariable showId: Long,
        @RequestBody request: ShowImageReferenceRequest,
    ) = service.update(showId, request.url, false)

    @DeleteMapping
    fun remove(
        @PathVariable showId: Long,
        @RequestBody request: ShowImageReferenceRequest,
    ) = service.update(showId, request.url, true)
}
