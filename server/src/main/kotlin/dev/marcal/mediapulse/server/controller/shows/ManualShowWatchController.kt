package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateRequest
import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateResponse
import dev.marcal.mediapulse.server.service.tv.ManualShowWatchCreateFlowService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shows")
class ManualShowWatchController(
    private val manualShowWatchCreateFlowService: ManualShowWatchCreateFlowService,
) {
    @PostMapping("/watches")
    fun createManualWatch(
        @RequestBody request: ManualShowWatchCreateRequest,
    ): ManualShowWatchCreateResponse = manualShowWatchCreateFlowService.execute(request)
}
