package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieCollectionMemberDto
import dev.marcal.mediapulse.server.api.movies.MovieCollectionMembersResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.crud.MovieCollectionCrudRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class MovieCollectionMembersService(
    private val movieCollectionCrudRepository: MovieCollectionCrudRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualMovieCatalogService: ManualMovieCatalogService,
) {
    @Transactional(readOnly = true)
    fun fetchMembers(collectionId: Long): MovieCollectionMembersResponse {
        val collection =
            movieCollectionCrudRepository.findCollection(collectionId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie collection not found")
        val tmdbCollection =
            tmdbApiClient.fetchMovieCollectionDetails(collection.tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb collection not found")
        val localMoviesByTmdbId =
            movieCollectionCrudRepository.findLocalMoviesByTmdbIds(
                tmdbCollection.parts.map { it.tmdbId },
            )

        return MovieCollectionMembersResponse(
            collectionId = collection.id,
            tmdbId = tmdbCollection.tmdbId,
            name = tmdbCollection.name,
            overview = tmdbCollection.overview,
            posterUrl = tmdbCollection.posterPath?.let(manualMovieCatalogService::buildTmdbImageUrl) ?: collection.posterUrl,
            backdropUrl = tmdbCollection.backdropPath?.let(manualMovieCatalogService::buildTmdbImageUrl) ?: collection.backdropUrl,
            members =
                tmdbCollection.parts.map { part ->
                    val localMovie = localMoviesByTmdbId[part.tmdbId]
                    MovieCollectionMemberDto(
                        tmdbId = part.tmdbId,
                        title = part.title ?: part.originalTitle ?: "TMDb ${part.tmdbId}",
                        originalTitle = part.originalTitle,
                        year = part.releaseYear,
                        overview = part.overview,
                        posterUrl = part.posterPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                        backdropUrl = part.backdropPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                        tmdbUrl = "https://www.themoviedb.org/movie/${part.tmdbId}",
                        localMovieId = localMovie?.movieId,
                        localSlug = localMovie?.slug,
                        inCatalog = localMovie != null,
                    )
                },
        )
    }
}
