package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentApplyMode
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentApplyRequest
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentField
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentPreviewRequest
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ShowMetadataEnrichmentServiceTest {
    private val shows = mockk<TvShowRepository>()
    private val catalog = mockk<ManualShowCatalogService>()
    private val terms = mockk<ShowTermsService>()
    private val credits = mockk<ShowCreditsService>()
    private val service = ShowMetadataEnrichmentService(shows, catalog, terms, credits)

    private val show = TvShow(id = 7, originalTitle = "Severance", fingerprint = "fp", tmdbId = "95396")
    private val snapshot =
        ManualShowCatalogService.TmdbShowSnapshot(
            tmdbId = "95396",
            title = "Ruptura",
            originalTitle = "Severance",
            overview = "Uma equipe separa as memórias do trabalho.",
            firstAirYear = 2022,
            posterPath = "/poster.jpg",
            backdropPath = null,
            posterUrl = "https://image/poster.jpg",
            backdropUrl = null,
        )

    @Test
    fun `preview marks missing metadata and images by default`() {
        every { shows.findById(7) } returns Optional.of(show)
        every { catalog.fetchTmdbShowSnapshot("95396") } returns snapshot
        every { catalog.buildTmdbImageCandidates(snapshot) } returns
            listOf(ManualShowCatalogService.TmdbImageCandidate("poster", "Poster", "https://image/poster.jpg", true))

        val result = service.preview(7, ShowMetadataEnrichmentPreviewRequest())

        assertEquals("95396", result.resolvedTmdbId)
        assertTrue(result.fields.first { it.field == ShowMetadataEnrichmentField.YEAR }.selectedByDefault)
        assertTrue(result.fields.first { it.field == ShowMetadataEnrichmentField.DESCRIPTION }.selectedByDefault)
        assertTrue(result.images.selectedByDefault)
    }

    @Test
    fun `apply selected metadata and synchronizes related tmdb data`() {
        val updated = show.copy(year = 2022, description = snapshot.overview)
        every { shows.findById(7) } returnsMany listOf(Optional.of(show), Optional.of(show), Optional.of(updated))
        every { catalog.fetchTmdbShowSnapshot("95396") } returns snapshot
        every { catalog.addShowTitle(7, "Ruptura") } just runs
        every { catalog.resolveShowSlug("Ruptura") } returns "ruptura"
        every { shows.save(any()) } answers { firstArg() }
        every { terms.syncFromTmdbIfLinked(7) } just runs
        every { credits.syncFromTmdbIfLinked(7) } just runs

        val result =
            service.apply(
                7,
                ShowMetadataEnrichmentApplyRequest(
                    mode = ShowMetadataEnrichmentApplyMode.SELECTED,
                    fields =
                        listOf(
                            ShowMetadataEnrichmentField.TITLE,
                            ShowMetadataEnrichmentField.YEAR,
                            ShowMetadataEnrichmentField.DESCRIPTION,
                        ),
                ),
            )

        assertEquals(3, result.appliedFields.size)
        verify { catalog.addShowTitle(7, "Ruptura") }
        verify { terms.syncFromTmdbIfLinked(7) }
        verify { credits.syncFromTmdbIfLinked(7) }
        verify(exactly = 0) { catalog.linkExternalIdIfAvailable(any(), Provider.TMDB, any()) }
    }
}
