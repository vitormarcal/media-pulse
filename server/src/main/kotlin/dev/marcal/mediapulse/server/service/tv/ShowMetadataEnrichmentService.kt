package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowExternalIdDto
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentApplyMode
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentApplyRequest
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentApplyResponse
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentField
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentFieldPreview
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentImageCandidatePreview
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentImagePreview
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentPreviewRequest
import dev.marcal.mediapulse.server.api.shows.ShowMetadataEnrichmentPreviewResponse
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class ShowMetadataEnrichmentService(
    private val tvShowRepository: TvShowRepository,
    private val manualShowCatalogService: ManualShowCatalogService,
    private val showTermsService: ShowTermsService,
    private val showCreditsService: ShowCreditsService,
) {
    @Transactional(readOnly = true)
    fun preview(
        showId: Long,
        request: ShowMetadataEnrichmentPreviewRequest,
    ): ShowMetadataEnrichmentPreviewResponse {
        val show = getShow(showId)
        val tmdbId = resolveTmdbId(showId, request.tmdbId)
        val snapshot =
            manualShowCatalogService.fetchTmdbShowSnapshot(tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb details not found")
        val candidates = manualShowCatalogService.buildTmdbImageCandidates(snapshot)
        val fields =
            listOf(
                previewField(ShowMetadataEnrichmentField.TITLE, "Título", show.originalTitle, snapshot.title, false),
                previewField(
                    ShowMetadataEnrichmentField.YEAR,
                    "Ano",
                    show.year?.toString(),
                    snapshot.firstAirYear?.toString(),
                    show.year == null,
                ),
                previewField(
                    ShowMetadataEnrichmentField.DESCRIPTION,
                    "Descrição",
                    show.description,
                    snapshot.overview,
                    show.description.isNullOrBlank(),
                ),
                previewField(ShowMetadataEnrichmentField.TMDB_ID, "TMDb ID", show.tmdbId, snapshot.tmdbId, show.tmdbId == null),
            )
        return ShowMetadataEnrichmentPreviewResponse(
            showId = showId,
            resolvedTmdbId = snapshot.tmdbId,
            title = show.originalTitle,
            fields = fields,
            images =
                ShowMetadataEnrichmentImagePreview(
                    currentCoverUrl = show.coverUrl,
                    suggestedPosterUrl = snapshot.posterUrl,
                    suggestedBackdropUrl = snapshot.backdropUrl,
                    candidates =
                        candidates.map {
                            ShowMetadataEnrichmentImageCandidatePreview(
                                key = it.key,
                                label = it.label,
                                imageUrl = it.imageUrl,
                                kind = it.key.uppercase(),
                                selectedByDefault = show.coverUrl == null || it.suggestedAsPrimary,
                                suggestedAsPrimary = it.suggestedAsPrimary,
                            )
                        },
                    available = candidates.isNotEmpty(),
                    missing = show.coverUrl == null,
                    changed = candidates.isNotEmpty(),
                    selectedByDefault = candidates.isNotEmpty() && show.coverUrl == null,
                ),
        )
    }

    @Transactional
    fun apply(
        showId: Long,
        request: ShowMetadataEnrichmentApplyRequest,
    ): ShowMetadataEnrichmentApplyResponse {
        var show = getShow(showId)
        val tmdbId = resolveTmdbId(showId, request.tmdbId)
        val snapshot =
            manualShowCatalogService.fetchTmdbShowSnapshot(tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb details not found")
        val selected = request.fields.toSet()
        val applied = linkedSetOf<ShowMetadataEnrichmentField>()
        var changed = false

        if (shouldApply(request.mode, selected, ShowMetadataEnrichmentField.TITLE, false) && !snapshot.title.isNullOrBlank()) {
            manualShowCatalogService.addShowTitle(showId, snapshot.title)
            applied += ShowMetadataEnrichmentField.TITLE
        }
        if (shouldApply(request.mode, selected, ShowMetadataEnrichmentField.YEAR, show.year == null) && snapshot.firstAirYear != null) {
            show = show.copy(year = snapshot.firstAirYear, updatedAt = Instant.now())
            changed = true
            applied += ShowMetadataEnrichmentField.YEAR
        }
        if (shouldApply(request.mode, selected, ShowMetadataEnrichmentField.DESCRIPTION, show.description.isNullOrBlank()) &&
            !snapshot.overview.isNullOrBlank()
        ) {
            show = show.copy(description = snapshot.overview, updatedAt = Instant.now())
            changed = true
            applied += ShowMetadataEnrichmentField.DESCRIPTION
        }
        if (changed) {
            if (show.slug == null) show = show.copy(slug = manualShowCatalogService.resolveShowSlug(snapshot.title ?: show.originalTitle))
            show = tvShowRepository.save(show)
        }
        if (shouldApply(request.mode, selected, ShowMetadataEnrichmentField.TMDB_ID, show.tmdbId == null)) {
            show = manualShowCatalogService.linkExternalIdIfAvailable(show, Provider.TMDB, snapshot.tmdbId)
            applied += ShowMetadataEnrichmentField.TMDB_ID
        }

        val imageSelection =
            request.imageSelection?.let {
                ManualShowCatalogService.TmdbImageSelection(it.selectedKeys.toSet(), it.primaryKey)
            }
        val imageResult =
            if (shouldApply(request.mode, selected, ShowMetadataEnrichmentField.IMAGES, show.coverUrl == null)) {
                manualShowCatalogService.assignSelectedTmdbImages(show, snapshot, imageSelection).also {
                    if (it.insertedCount > 0) applied += ShowMetadataEnrichmentField.IMAGES
                }
            } else {
                ManualShowCatalogService.TmdbImageAssignmentResult(0)
            }

        showTermsService.syncFromTmdbIfLinked(showId)
        showCreditsService.syncFromTmdbIfLinked(showId)
        val refreshed = getShow(showId)
        return ShowMetadataEnrichmentApplyResponse(
            showId = showId,
            slug = refreshed.slug,
            title = refreshed.originalTitle,
            appliedFields = applied.toList(),
            coverAssigned = imageResult.primaryImageUrl != null,
            externalIds =
                listOfNotNull(
                    refreshed.tmdbId?.let { ShowExternalIdDto("TMDB", it) },
                    refreshed.tvdbId?.let { ShowExternalIdDto("TVDB", it) },
                    refreshed.imdbId?.let { ShowExternalIdDto("IMDB", it) },
                ),
        )
    }

    private fun getShow(showId: Long) =
        tvShowRepository.findById(showId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found")
        }

    private fun resolveTmdbId(
        showId: Long,
        requested: String?,
    ): String =
        requested?.trim()?.ifBlank { null } ?: getShow(showId).tmdbId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "tmdbId é obrigatório quando a série ainda não tem vínculo TMDb")

    private fun shouldApply(
        mode: ShowMetadataEnrichmentApplyMode,
        selected: Set<ShowMetadataEnrichmentField>,
        field: ShowMetadataEnrichmentField,
        missing: Boolean,
    ) = when (mode) {
        ShowMetadataEnrichmentApplyMode.MISSING -> missing
        ShowMetadataEnrichmentApplyMode.SELECTED -> field in selected
    }

    private fun previewField(
        field: ShowMetadataEnrichmentField,
        label: String,
        current: String?,
        suggested: String?,
        missing: Boolean,
    ): ShowMetadataEnrichmentFieldPreview {
        val available = !suggested.isNullOrBlank()
        return ShowMetadataEnrichmentFieldPreview(
            field,
            label,
            current,
            suggested,
            available,
            missing,
            changed = available && current != suggested,
            selectedByDefault = available && missing,
        )
    }
}
