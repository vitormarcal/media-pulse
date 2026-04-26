package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MoviePersonFilmographyMemberDto
import dev.marcal.mediapulse.server.api.movies.MoviePersonFilmographyResponse
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.movie.MovieCreditType
import dev.marcal.mediapulse.server.repository.crud.MovieCreditAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieCreditsCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MoviePersonRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class MoviePersonFilmographyService(
    private val moviePersonRepository: MoviePersonRepository,
    private val movieCreditAssignmentRepository: MovieCreditAssignmentRepository,
    private val movieCreditsCrudRepository: MovieCreditsCrudRepository,
    private val tmdbApiClient: TmdbApiClient,
    private val manualMovieCatalogService: ManualMovieCatalogService,
) {
    private val relevantCrewJobs =
        setOf(
            "Director",
            "Writer",
            "Screenplay",
            "Story",
            "Editor",
            "Producer",
            "Director of Photography",
            "Original Music Composer",
        )

    @Transactional
    fun fetchFilmography(personId: Long): MoviePersonFilmographyResponse {
        val person =
            moviePersonRepository.findById(personId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Movie person not found")
            }

        val filmography =
            tmdbApiClient.fetchPersonMovieCredits(person.tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb person filmography not found")

        data class FilmographyItem(
            val tmdbId: String,
            val title: String,
            val originalTitle: String?,
            val overview: String?,
            val year: Int?,
            val posterUrl: String?,
            val backdropUrl: String?,
            val roleLabels: MutableList<String>,
        )

        val merged = linkedMapOf<String, FilmographyItem>()

        filmography.cast
            .sortedBy { it.order ?: Int.MAX_VALUE }
            .forEach { credit ->
                val title = credit.title ?: credit.originalTitle ?: return@forEach
                val item =
                    merged.getOrPut(credit.tmdbId) {
                        FilmographyItem(
                            tmdbId = credit.tmdbId,
                            title = title,
                            originalTitle = credit.originalTitle,
                            overview = credit.overview,
                            year = credit.releaseYear,
                            posterUrl = credit.posterPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                            backdropUrl = credit.backdropPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                            roleLabels = mutableListOf(),
                        )
                    }
                val label = credit.character?.takeIf { it.isNotBlank() } ?: "Elenco"
                if (label !in item.roleLabels) item.roleLabels.add(label)
            }

        filmography.crew
            .filter { it.job in relevantCrewJobs }
            .forEach { credit ->
                val title = credit.title ?: credit.originalTitle ?: return@forEach
                val item =
                    merged.getOrPut(credit.tmdbId) {
                        FilmographyItem(
                            tmdbId = credit.tmdbId,
                            title = title,
                            originalTitle = credit.originalTitle,
                            overview = credit.overview,
                            year = credit.releaseYear,
                            posterUrl = credit.posterPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                            backdropUrl = credit.backdropPath?.let(manualMovieCatalogService::buildTmdbImageUrl),
                            roleLabels = mutableListOf(),
                        )
                    }
                val label = credit.job ?: credit.department ?: "Equipe"
                if (label !in item.roleLabels) item.roleLabels.add(label)
            }

        val items =
            merged.values
                .sortedWith(compareByDescending<FilmographyItem> { it.year ?: Int.MIN_VALUE }.thenBy { it.title })
                .toList()

        val localMoviesByTmdbId = movieCreditsCrudRepository.findLocalMoviesByTmdbIds(items.map { it.tmdbId })

        filmography.cast.forEach { credit ->
            val localMovie = localMoviesByTmdbId[credit.tmdbId] ?: return@forEach
            movieCreditAssignmentRepository.upsert(
                MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                    movieId = localMovie.movieId,
                    personId = person.id,
                    creditType = MovieCreditType.CAST,
                    characterName = credit.character ?: "",
                    billingOrder = credit.order,
                ),
            )
        }

        filmography.crew
            .filter { it.job in relevantCrewJobs }
            .forEach { credit ->
                val localMovie = localMoviesByTmdbId[credit.tmdbId] ?: return@forEach
                movieCreditAssignmentRepository.upsert(
                    MovieCreditAssignmentRepository.UpsertMovieCreditRequest(
                        movieId = localMovie.movieId,
                        personId = person.id,
                        creditType = MovieCreditType.CREW,
                        department = credit.department ?: "",
                        job = credit.job ?: "",
                    ),
                )
            }

        return MoviePersonFilmographyResponse(
            personId = person.id,
            tmdbId = person.tmdbId,
            name = person.name,
            profileUrl = person.profileUrl,
            members =
                items.map { item ->
                    val localMovie = localMoviesByTmdbId[item.tmdbId]
                    MoviePersonFilmographyMemberDto(
                        tmdbId = item.tmdbId,
                        title = item.title,
                        originalTitle = item.originalTitle,
                        year = item.year,
                        overview = item.overview,
                        posterUrl = item.posterUrl,
                        backdropUrl = item.backdropUrl,
                        tmdbUrl = "https://www.themoviedb.org/movie/${item.tmdbId}",
                        localMovieId = localMovie?.movieId,
                        localSlug = localMovie?.slug,
                        inCatalog = localMovie != null,
                        roleLabel = item.roleLabels.take(3).joinToString(" · "),
                    )
                },
        )
    }
}
