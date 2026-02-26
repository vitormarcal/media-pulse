package dev.marcal.mediapulse.server.controller.plex

import dev.marcal.mediapulse.server.service.plex.import.PlexMusicImportService
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestBody
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/plex/music")
class PlexMusicImportController(
    private val plexMusicImportService: PlexMusicImportService,
) {
    data class MusicImportRequest(
        val sectionKey: String? = null,
        val pageSize: Int? = 200,
    )

    data class MusicImportResponse(
        val sectionKey: String?,
        val stats: PlexMusicImportService.ImportStats,
    )

    @PostMapping("/import")
    suspend fun importMusic(
        @RequestBody req: MusicImportRequest,
    ): MusicImportResponse {
        val stats = plexMusicImportService.importAllMusicLibrary(req.sectionKey, req.pageSize ?: 200)
        return MusicImportResponse(req.sectionKey, stats)
    }
}
