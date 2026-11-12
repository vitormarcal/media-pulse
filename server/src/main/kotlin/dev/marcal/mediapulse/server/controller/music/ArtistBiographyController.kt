package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.ArtistBiographyApplyRequest
import dev.marcal.mediapulse.server.service.music.ArtistBiographyService
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/music/artists/{artistId}/biography")
class ArtistBiographyController(
    private val service: ArtistBiographyService,
) {
    @PostMapping("/preview")
    suspend fun preview(
        @PathVariable artistId: Long,
    ) = service.preview(artistId)

    @PostMapping
    fun apply(
        @PathVariable artistId: Long,
        @RequestBody request: ArtistBiographyApplyRequest,
    ) = service.apply(artistId, request.token)
}
