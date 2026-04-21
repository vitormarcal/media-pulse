package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.shows.ManualShowCatalogCreateRequest
import dev.marcal.mediapulse.server.api.shows.ManualShowCatalogCreateResponse
import dev.marcal.mediapulse.server.api.shows.ShowCatalogSuggestionsResponse
import dev.marcal.mediapulse.server.service.tv.ManualShowCatalogCreateFlowService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/shows")
class ShowCatalogController(
    private val manualShowCatalogCreateFlowService: ManualShowCatalogCreateFlowService,
) {
    @GetMapping("/catalog/suggestions")
    fun suggestCatalogEntry(
        @RequestParam q: String,
    ): ShowCatalogSuggestionsResponse = manualShowCatalogCreateFlowService.suggest(q)

    @PostMapping("/catalog")
    fun createCatalogEntry(
        @RequestBody request: ManualShowCatalogCreateRequest,
    ): ManualShowCatalogCreateResponse = manualShowCatalogCreateFlowService.execute(request)
}
