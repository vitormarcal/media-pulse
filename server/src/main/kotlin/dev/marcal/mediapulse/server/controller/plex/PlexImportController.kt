package dev.marcal.mediapulse.server.controller.plex

import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.service.plex.PlexImportService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/plex")
class PlexImportController(
    private val plexImportService: PlexImportService
) {
    data class ImportRequest(
        val baseUrl: String,
        val token: String,
        val sectionKey: String? = null,
        val pageSize: Int? = 200
    )
    data class ImportResponse(
        val sectionKey: String?,
        val stats: PlexImportService.ImportStats
    )

    @PostMapping("/import")
    suspend fun import(@RequestBody req: ImportRequest): ImportResponse {
        val auth = PlexApiClient.PlexAuth(req.baseUrl, req.token)
        val stats = plexImportService.importAllArtistsAndAlbums(auth, req.sectionKey, req.pageSize ?: 200)
        return ImportResponse(req.sectionKey, stats)
    }
}
