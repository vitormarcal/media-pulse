package dev.marcal.mediapulse.server.service.person

import dev.marcal.mediapulse.server.api.movies.PersonDetailsResponse
import dev.marcal.mediapulse.server.api.movies.PersonTmdbProfileDto
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.service.movie.ManualMovieCatalogService
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class PersonDetailsService(
    private val repository: MovieQueryRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualMovieCatalogService: ManualMovieCatalogService,
) {
    @Transactional(readOnly = true)
    fun fetchDetails(slug: String): PersonDetailsResponse {
        val local = repository.getPersonDetails(slug)
        val tmdbProfile =
            tmdbApiClient.fetchPersonDetails(local.tmdbId)?.let { profile ->
                PersonTmdbProfileDto(
                    biography = profile.biography,
                    birthday = profile.birthday,
                    deathday = profile.deathday,
                    placeOfBirth = profile.placeOfBirth,
                    knownForDepartment = profile.knownForDepartment,
                    aliases = profile.alsoKnownAs,
                    homepage = profile.homepage,
                    imdbId = profile.imdbId,
                    popularity = profile.popularity,
                    profileUrl = profile.profilePath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                )
            }

        return local.copy(
            profileUrl = tmdbProfile?.profileUrl ?: local.profileUrl,
            tmdbProfile = tmdbProfile,
        )
    }
}
