package dev.marcal.mediapulse.server.controller.admin

import dev.marcal.mediapulse.server.controller.eventsource.EventSourceController
import dev.marcal.mediapulse.server.controller.music.MusicAlbumDuplicateReviewController
import dev.marcal.mediapulse.server.controller.music.MusicArtistDuplicateReviewController
import dev.marcal.mediapulse.server.controller.music.MusicDuplicateReviewController
import dev.marcal.mediapulse.server.controller.musicbrainz.MusicBrainzEnrichmentController
import dev.marcal.mediapulse.server.controller.plex.PlexMusicImportController
import dev.marcal.mediapulse.server.controller.spotify.SpotifyBackfillController
import dev.marcal.mediapulse.server.controller.spotify.SpotifyExtendedImportController
import dev.marcal.mediapulse.server.controller.spotify.SpotifyImportController
import dev.marcal.mediapulse.server.controller.spotify.SpotifyStatusController
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.RequestMapping
import kotlin.test.assertTrue

class AdminRouteNamespaceContractTest {
    @Test
    fun `operational controllers should live under api admin`() {
        val controllers =
            listOf(
                MovieAdminController::class.java,
                ShowAdminController::class.java,
                PeopleAdminController::class.java,
                EventSourceController::class.java,
                MusicBrainzEnrichmentController::class.java,
                PlexMusicImportController::class.java,
                SpotifyImportController::class.java,
                SpotifyExtendedImportController::class.java,
                SpotifyBackfillController::class.java,
                SpotifyStatusController::class.java,
                MusicDuplicateReviewController::class.java,
                MusicAlbumDuplicateReviewController::class.java,
                MusicArtistDuplicateReviewController::class.java,
            )

        controllers.forEach { controller ->
            val paths = controller.getAnnotation(RequestMapping::class.java).value
            assertTrue(paths.single().startsWith("/api/admin/"), "$controller should use /api/admin/**")
        }
    }
}
