package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.ManualMovieExternalIdView
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentApplyMode
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentApplyRequest
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentApplyResponse
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentField
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentFieldPreview
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentImageCandidatePreview
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentImagePreview
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentPreviewRequest
import dev.marcal.mediapulse.server.api.movies.MovieEnrichmentPreviewResponse
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class MovieMetadataEnrichmentService(
    private val movieQueryRepository: MovieQueryRepository,
    private val movieRepository: MovieRepository,
    private val externalIdentifierRepository: ExternalIdentifierRepository,
    private val manualMovieCatalogService: ManualMovieCatalogService,
    private val movieTermsService: MovieTermsService,
    private val movieCreditsService: MovieCreditsService,
) {
    @Transactional(readOnly = true)
    fun preview(
        movieId: Long,
        request: MovieEnrichmentPreviewRequest,
    ): MovieEnrichmentPreviewResponse {
        val details = movieQueryRepository.getMovieDetails(movieId)
        val tmdbId = resolveTmdbId(movieId, request.tmdbId)
        val snapshot =
            manualMovieCatalogService.fetchTmdbMovieSnapshot(tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb details not found")

        val hasTmdb = details.externalIds.any { it.provider == Provider.TMDB.name }
        val hasImdb = details.externalIds.any { it.provider == Provider.IMDB.name }
        val imageCandidates = manualMovieCatalogService.buildTmdbImageCandidates(snapshot)

        val fields =
            listOf(
                previewField(
                    field = MovieEnrichmentField.TITLE,
                    label = "Título",
                    currentValue = details.title,
                    suggestedValue = snapshot.title,
                    missing = false,
                ),
                previewField(
                    field = MovieEnrichmentField.YEAR,
                    label = "Ano",
                    currentValue = details.year?.toString(),
                    suggestedValue = snapshot.releaseYear?.toString(),
                    missing = details.year == null,
                ),
                previewField(
                    field = MovieEnrichmentField.DESCRIPTION,
                    label = "Descrição",
                    currentValue = details.description,
                    suggestedValue = snapshot.overview,
                    missing = details.description.isNullOrBlank(),
                ),
                previewField(
                    field = MovieEnrichmentField.TMDB_ID,
                    label = "TMDb ID",
                    currentValue = details.externalIds.firstOrNull { it.provider == Provider.TMDB.name }?.externalId,
                    suggestedValue = snapshot.tmdbId,
                    missing = !hasTmdb,
                ),
                previewField(
                    field = MovieEnrichmentField.IMDB_ID,
                    label = "IMDb ID",
                    currentValue = details.externalIds.firstOrNull { it.provider == Provider.IMDB.name }?.externalId,
                    suggestedValue = snapshot.imdbId,
                    missing = !hasImdb,
                ),
            )

        val images =
            MovieEnrichmentImagePreview(
                currentCoverUrl = details.coverUrl,
                suggestedPosterUrl = snapshot.posterUrl,
                suggestedBackdropUrl = snapshot.backdropUrl,
                candidates =
                    imageCandidates.map { candidate ->
                        MovieEnrichmentImageCandidatePreview(
                            key = candidate.key,
                            label = candidate.label,
                            imageUrl = candidate.imageUrl,
                            kind = candidate.key.uppercase(),
                            selectedByDefault = details.coverUrl == null || candidate.suggestedAsPrimary,
                            suggestedAsPrimary = candidate.suggestedAsPrimary,
                        )
                    },
                available = imageCandidates.isNotEmpty(),
                missing = details.coverUrl == null,
                changed = imageCandidates.isNotEmpty(),
                selectedByDefault = imageCandidates.isNotEmpty(),
            )

        return MovieEnrichmentPreviewResponse(
            movieId = movieId,
            resolvedTmdbId = snapshot.tmdbId,
            title = details.title,
            fields = fields,
            images = images,
        )
    }

    @Transactional
    fun apply(
        movieId: Long,
        request: MovieEnrichmentApplyRequest,
    ): MovieEnrichmentApplyResponse {
        val movie =
            movieRepository.findById(movieId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found")
            }
        val details = movieQueryRepository.getMovieDetails(movieId)
        val tmdbId = resolveTmdbId(movieId, request.tmdbId)
        val snapshot =
            manualMovieCatalogService.fetchTmdbMovieSnapshot(tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb details not found")

        val applyMode = request.mode
        val selectedFields = request.fields.toSet()
        val appliedFields = linkedSetOf<MovieEnrichmentField>()
        var mutableMovie = movie
        var movieChanged = false

        if (shouldApply(applyMode, selectedFields, MovieEnrichmentField.TITLE, false) && !snapshot.title.isNullOrBlank()) {
            manualMovieCatalogService.addMovieTitle(movieId = movieId, title = snapshot.title, isPrimary = false)
            appliedFields += MovieEnrichmentField.TITLE
        }

        if (shouldApply(applyMode, selectedFields, MovieEnrichmentField.YEAR, movie.year == null) && snapshot.releaseYear != null) {
            mutableMovie = mutableMovie.copy(year = snapshot.releaseYear, updatedAt = Instant.now())
            movieChanged = true
            appliedFields += MovieEnrichmentField.YEAR
        }

        if (shouldApply(applyMode, selectedFields, MovieEnrichmentField.DESCRIPTION, movie.description.isNullOrBlank()) &&
            !snapshot.overview.isNullOrBlank()
        ) {
            mutableMovie = mutableMovie.copy(description = snapshot.overview, updatedAt = Instant.now())
            movieChanged = true
            appliedFields += MovieEnrichmentField.DESCRIPTION
        }

        if (shouldApply(applyMode, selectedFields, MovieEnrichmentField.TMDB_ID, !hasExternalId(details, Provider.TMDB)) &&
            snapshot.tmdbId.isNotBlank()
        ) {
            manualMovieCatalogService.linkExternalIdIfAvailable(movieId, Provider.TMDB, snapshot.tmdbId)
            appliedFields += MovieEnrichmentField.TMDB_ID
        }

        if (shouldApply(applyMode, selectedFields, MovieEnrichmentField.IMDB_ID, !hasExternalId(details, Provider.IMDB)) &&
            !snapshot.imdbId.isNullOrBlank()
        ) {
            manualMovieCatalogService.linkExternalIdIfAvailable(movieId, Provider.IMDB, snapshot.imdbId)
            appliedFields += MovieEnrichmentField.IMDB_ID
        }

        if (movieChanged) {
            if (mutableMovie.slug == null) {
                val slugSource = snapshot.title ?: mutableMovie.originalTitle
                mutableMovie = mutableMovie.copy(slug = manualMovieCatalogService.resolveMovieSlug(slugSource), updatedAt = Instant.now())
            }
            mutableMovie = movieRepository.save(mutableMovie)
        }

        mutableMovie = manualMovieCatalogService.assignTmdbCollection(mutableMovie, snapshot)

        val imageSelection =
            request.imageSelection?.let {
                ManualMovieCatalogService.TmdbImageSelection(
                    selectedKeys = it.selectedKeys.toSet(),
                    primaryKey = it.primaryKey,
                )
            }

        val imageResult =
            if (shouldApply(applyMode, selectedFields, MovieEnrichmentField.IMAGES, movie.coverUrl == null)) {
                val result = manualMovieCatalogService.assignSelectedTmdbImages(mutableMovie, snapshot, imageSelection)
                if (result.insertedCount > 0) {
                    appliedFields += MovieEnrichmentField.IMAGES
                }
                result
            } else {
                ManualMovieCatalogService.TmdbImageAssignmentResult(insertedCount = 0)
            }

        val refreshedMovie = movieRepository.findById(movieId).orElse(mutableMovie)
        movieTermsService.syncFromTmdbIfLinked(movieId)
        movieCreditsService.syncFromTmdbIfLinked(movieId)
        val externalIds =
            externalIdentifierRepository
                .findByEntityTypeAndEntityId(EntityType.MOVIE, movieId)
                .sortedWith(compareBy({ it.provider.name }, { it.externalId }))
                .map { ManualMovieExternalIdView(provider = it.provider.name, externalId = it.externalId) }

        return MovieEnrichmentApplyResponse(
            movieId = movieId,
            slug = refreshedMovie.slug,
            title = refreshedMovie.originalTitle,
            appliedFields = appliedFields.toList(),
            coverAssigned = imageResult.primaryImageUrl != null,
            externalIds = externalIds,
        )
    }

    private fun resolveTmdbId(
        movieId: Long,
        requestTmdbId: String?,
    ): String =
        requestTmdbId?.trim()?.ifBlank { null }
            ?: externalIdentifierRepository
                .findFirstByEntityTypeAndProviderAndEntityId(EntityType.MOVIE, Provider.TMDB, movieId)
                ?.externalId
            ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "tmdbId é obrigatório quando o filme ainda não tem vínculo TMDb")

    private fun hasExternalId(
        details: dev.marcal.mediapulse.server.api.movies.MovieDetailsResponse,
        provider: Provider,
    ): Boolean = details.externalIds.any { it.provider == provider.name }

    private fun shouldApply(
        mode: MovieEnrichmentApplyMode,
        selectedFields: Set<MovieEnrichmentField>,
        field: MovieEnrichmentField,
        missing: Boolean,
    ): Boolean =
        when (mode) {
            MovieEnrichmentApplyMode.MISSING -> missing
            MovieEnrichmentApplyMode.SELECTED -> selectedFields.contains(field)
        }

    private fun previewField(
        field: MovieEnrichmentField,
        label: String,
        currentValue: String?,
        suggestedValue: String?,
        missing: Boolean,
    ): MovieEnrichmentFieldPreview {
        val available = !suggestedValue.isNullOrBlank()
        val changed = available && currentValue != suggestedValue
        return MovieEnrichmentFieldPreview(
            field = field,
            label = label,
            currentValue = currentValue,
            suggestedValue = suggestedValue,
            available = available,
            missing = missing,
            changed = changed,
            selectedByDefault = available && missing,
        )
    }
}
