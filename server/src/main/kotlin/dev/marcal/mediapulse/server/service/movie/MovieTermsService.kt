package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieTermCreateRequest
import dev.marcal.mediapulse.server.api.movies.MovieTermDto
import dev.marcal.mediapulse.server.api.movies.MovieTermKindDto
import dev.marcal.mediapulse.server.api.movies.MovieTermsSyncResponse
import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.model.movie.MovieTerm
import dev.marcal.mediapulse.server.model.movie.MovieTermKind
import dev.marcal.mediapulse.server.model.movie.MovieTermSource
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTermAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieTermRepository
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class MovieTermsService(
    private val movieRepository: MovieRepository,
    private val movieQueryRepository: MovieQueryRepository,
    private val movieTermRepository: MovieTermRepository,
    private val movieTermAssignmentRepository: MovieTermAssignmentRepository,
    private val manualMovieCatalogService: ManualMovieCatalogService,
) {
    @Transactional
    fun syncFromTmdb(movieId: Long): MovieTermsSyncResponse {
        val movie = requireMovie(movieId)
        val tmdbId =
            movieQueryRepository
                .getMovieDetails(movieId)
                .externalIds
                .firstOrNull { it.provider == "TMDB" }
                ?.externalId
                ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Filme sem vínculo TMDb")

        val snapshot =
            manualMovieCatalogService.fetchTmdbMovieSnapshot(tmdbId)
                ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "TMDb details not found")

        val syncedTerms =
            snapshot.genres.map { MovieTermKind.GENRE to it } +
                snapshot.keywords.map { MovieTermKind.TAG to it }

        val termIds =
            syncedTerms
                .map { (kind, name) -> upsertTermAndAssignment(movie, kind, name, MovieTermSource.TMDB).id }
                .distinct()

        return MovieTermsSyncResponse(
            movieId = movieId,
            syncedCount = termIds.size,
            visibleCount = movieQueryRepository.getMovieTerms(movieId).count { it.active },
        )
    }

    @Transactional
    fun syncFromTmdbIfLinked(movieId: Long) {
        val hasTmdbLink =
            movieQueryRepository
                .getMovieDetails(movieId)
                .externalIds
                .any { it.provider == "TMDB" }

        if (hasTmdbLink) {
            syncFromTmdb(movieId)
        }
    }

    @Transactional
    fun addTerm(
        movieId: Long,
        request: MovieTermCreateRequest,
    ): MovieTermDto {
        val movie = requireMovie(movieId)
        val term = upsertTermAndAssignment(movie, request.kind.toModel(), request.name, MovieTermSource.USER)
        return movieQueryRepository.getMovieTerms(movieId).first { it.id == term.id }
    }

    @Transactional
    fun updateGlobalVisibility(
        termId: Long,
        hidden: Boolean,
    ): MovieTermDto {
        val term =
            movieTermRepository.findById(termId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Term not found")
            }

        val saved = movieTermRepository.save(term.copy(hidden = hidden, updatedAt = Instant.now()))
        return movieQueryRepository.findTerm(saved.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Term not found")
    }

    @Transactional
    fun updateMovieVisibility(
        movieId: Long,
        termId: Long,
        hidden: Boolean,
    ): MovieTermDto {
        requireMovie(movieId)
        val updated = movieTermAssignmentRepository.updateVisibility(movieId, termId, hidden)
        if (updated == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie term assignment not found")
        }
        return movieQueryRepository.getMovieTerms(movieId).firstOrNull { it.id == termId }
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Term not found for movie")
    }

    private fun requireMovie(movieId: Long): Movie =
        movieRepository.findById(movieId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found")
        }

    private fun upsertTermAndAssignment(
        movie: Movie,
        kind: MovieTermKind,
        rawName: String,
        assignmentSource: MovieTermSource,
    ): MovieTerm {
        val name = rawName.trim().replace("\\s+".toRegex(), " ")
        if (name.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name deve ser preenchido")
        }

        val normalizedName = name.lowercase()
        val existing = movieTermRepository.findByKindAndNormalizedName(kind, normalizedName)
        val term =
            existing
                ?: movieTermRepository.save(
                    MovieTerm(
                        name = name,
                        normalizedName = normalizedName,
                        slug = SlugTextUtil.normalize(name, maxLength = 64),
                        kind = kind,
                        source = assignmentSource,
                    ),
                )

        val refreshedTerm =
            if (existing != null && existing.hidden) {
                movieTermRepository.save(existing.copy(hidden = false, updatedAt = Instant.now()))
            } else {
                term
            }

        movieTermAssignmentRepository.upsert(movie.id, refreshedTerm.id, assignmentSource)
        return refreshedTerm
    }

    private fun MovieTermKindDto.toModel(): MovieTermKind = MovieTermKind.valueOf(name)
}
