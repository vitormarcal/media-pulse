package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.shows.ExistingShowWatchCreateRequest
import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateRequest
import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateResponse
import dev.marcal.mediapulse.server.service.tv.ExistingShowWatchCreateFlowService
import dev.marcal.mediapulse.server.service.tv.ManualShowWatchCreateFlowService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shows")
class ManualShowWatchController(
    private val manualShowWatchCreateFlowService: ManualShowWatchCreateFlowService,
    private val existingShowWatchCreateFlowService: ExistingShowWatchCreateFlowService,
) {
    @PostMapping("/watches")
    fun createManualWatch(
        @RequestBody request: ManualShowWatchCreateRequest,
    ): ManualShowWatchCreateResponse = manualShowWatchCreateFlowService.execute(request)

    @PostMapping("/{showId}/watches")
    fun createExistingShowWatch(
        @PathVariable showId: Long,
        @RequestBody request: ExistingShowWatchCreateRequest,
    ): ManualShowWatchCreateResponse = existingShowWatchCreateFlowService.execute(showId, request)
}
