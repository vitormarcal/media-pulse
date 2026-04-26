package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieCompanyMemberDto
import dev.marcal.mediapulse.server.api.movies.MovieCompanyMembersResponse
import dev.marcal.mediapulse.server.model.movie.MovieCompanyType
import dev.marcal.mediapulse.server.repository.crud.MovieCompaniesCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCompanyAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCompanyRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class MovieCompanyMembersService(
    private val movieCompanyRepository: MovieCompanyRepository,
    private val movieCompaniesCrudRepository: MovieCompaniesCrudRepository,
    private val movieCompanyAssignmentRepository: MovieCompanyAssignmentRepository,
    private val tmdbApiClient: dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient,
    private val manualMovieCatalogService: ManualMovieCatalogService,
) {
    @Transactional
    fun fetchMembers(companyId: Long): MovieCompanyMembersResponse {
        val company =
            movieCompanyRepository.findById(companyId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Movie company not found")
            }

        val tmdbMovies =
            tmdbApiClient.fetchCompanyMovies(company.tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb company movies not found")

        val localMoviesByTmdbId = movieCompaniesCrudRepository.findLocalMoviesByTmdbIds(tmdbMovies.movies.map { it.tmdbId })

        localMoviesByTmdbId.values.forEach { localMovie ->
            movieCompanyAssignmentRepository.upsert(
                MovieCompanyAssignmentRepository.UpsertMovieCompanyRequest(
                    movieId = localMovie.movieId,
                    companyId = company.id,
                    companyType = MovieCompanyType.PRODUCTION,
                ),
            )
        }

        return MovieCompanyMembersResponse(
            companyId = company.id,
            tmdbId = company.tmdbId,
            name = company.name,
            logoUrl = company.logoUrl,
            originCountry = company.originCountry,
            members =
                tmdbMovies.movies.map { movie ->
                    val localMovie = localMoviesByTmdbId[movie.tmdbId]
                    MovieCompanyMemberDto(
                        tmdbId = movie.tmdbId,
                        title = movie.title ?: movie.originalTitle ?: movie.tmdbId,
                        originalTitle = movie.originalTitle,
                        year = movie.releaseYear,
                        overview = movie.overview,
                        posterUrl = movie.posterPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                        backdropUrl = null,
                        tmdbUrl = "https://www.themoviedb.org/movie/${movie.tmdbId}",
                        localMovieId = localMovie?.movieId,
                        localSlug = localMovie?.slug,
                        inCatalog = localMovie != null,
                    )
                },
        )
    }
}
