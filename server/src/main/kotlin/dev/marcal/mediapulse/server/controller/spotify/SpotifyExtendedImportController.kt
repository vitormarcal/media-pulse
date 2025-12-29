package dev.marcal.mediapulse.server.controller.spotify

import dev.marcal.mediapulse.server.controller.spotify.dto.SpotifyImportResponse
import dev.marcal.mediapulse.server.service.spotify.SpotifyExtendedImportService
import org.springframework.http.MediaType
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestPart
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.multipart.MultipartFile

@RequestMapping("/api/spotify/extended")
@RestController
class SpotifyExtendedImportController(
    private val importService: SpotifyExtendedImportService,
) {
    @PostMapping("/import", consumes = [MediaType.MULTIPART_FORM_DATA_VALUE])
    suspend fun import(
        @RequestPart("file") file: MultipartFile,
    ): ResponseEntity<SpotifyImportResponse> {
        val imported = importService.importExtendedHistory(file)
        return ResponseEntity.ok(
            SpotifyImportResponse(
                imported = imported,
                resetCursor = false,
            ),
        )
    }
}
