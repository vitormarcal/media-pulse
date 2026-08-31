package dev.marcal.mediapulse.server.controller

import dev.marcal.mediapulse.server.controller.games.GameCatalogController
import dev.marcal.mediapulse.server.controller.movies.MovieCatalogController
import dev.marcal.mediapulse.server.controller.movies.MoviesController
import dev.marcal.mediapulse.server.controller.music.MusicBrainzPageEnrichmentController
import dev.marcal.mediapulse.server.controller.people.PeopleController
import dev.marcal.mediapulse.server.controller.shows.ShowCatalogController
import org.junit.jupiter.api.Test
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PostMapping
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class ExternalActionHttpMethodContractTest {
    @Test
    fun `provider-backed actions should use post`() {
        val actions =
            listOf(
                GameCatalogController::class.java to "suggestions",
                ShowCatalogController::class.java to "suggestCatalogEntry",
                MovieCatalogController::class.java to "suggestCatalogEntry",
                MovieCatalogController::class.java to "collectionTmdbMembers",
                MovieCatalogController::class.java to "companyTmdbMembers",
                MoviesController::class.java to "movieTmdbCreditCandidates",
                MusicBrainzPageEnrichmentController::class.java to "newArtistCandidates",
                MusicBrainzPageEnrichmentController::class.java to "albumCandidates",
                MusicBrainzPageEnrichmentController::class.java to "albumPreview",
                MusicBrainzPageEnrichmentController::class.java to "artistCandidates",
                MusicBrainzPageEnrichmentController::class.java to "artistDiscography",
                PeopleController::class.java to "tmdbFilmography",
                PeopleController::class.java to "tmdbShowFilmography",
            )

        actions.forEach { (controller, methodName) ->
            val method = controller.declaredMethods.single { it.name == methodName }
            assertNotNull(method.getAnnotation(PostMapping::class.java), "$controller.$methodName should use POST")
            assertNull(method.getAnnotation(GetMapping::class.java), "$controller.$methodName should not use GET")
        }
    }
}
