package dev.marcal.mediapulse.server.controller.plex

import dev.marcal.mediapulse.server.service.plex.import.PlexImportService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/plex")
class PlexImportController(
    private val plexImportService: PlexImportService,
) {
    data class ImportRequest(
        val sectionKey: String? = null,
        val pageSize: Int? = 200,
    )

    data class ImportResponse(
        val sectionKey: String?,
        val stats: PlexImportService.ImportStats,
    )

    @PostMapping("/import")
    suspend fun import(
        @RequestBody req: ImportRequest,
    ): ImportResponse {
        val stats = plexImportService.importAllArtistsAndAlbums(req.sectionKey, req.pageSize ?: 200)
        return ImportResponse(req.sectionKey, stats)
    }
}
