package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieListAttachRequest
import dev.marcal.mediapulse.server.api.movies.MovieListCoverUpdateRequest
import dev.marcal.mediapulse.server.api.movies.MovieListCreateRequest
import dev.marcal.mediapulse.server.api.movies.MovieListOrderUpdateRequest
import dev.marcal.mediapulse.server.api.movies.MovieListSummaryDto
import dev.marcal.mediapulse.server.model.movie.MovieList
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import dev.marcal.mediapulse.server.repository.crud.MovieListItemCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieListRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class MovieListsService(
    private val movieRepository: MovieRepository,
    private val movieListRepository: MovieListRepository,
    private val movieListItemCrudRepository: MovieListItemCrudRepository,
    private val movieQueryRepository: MovieQueryRepository,
) {
    @Transactional(readOnly = true)
    fun listAll(): List<MovieListSummaryDto> = movieQueryRepository.listMovieLists()

    @Transactional
    fun create(request: MovieListCreateRequest): MovieListSummaryDto {
        val list = createList(request.name, request.description)
        return movieQueryRepository.getMovieListSummary(list.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie list not found")
    }

    @Transactional
    fun attachMovie(
        movieId: Long,
        request: MovieListAttachRequest,
    ): MovieListSummaryDto {
        requireMovie(movieId)
        val list =
            if (request.listId != null) {
                movieListRepository.findById(request.listId).orElseThrow {
                    ResponseStatusException(HttpStatus.NOT_FOUND, "Movie list not found")
                }
            } else {
                createList(
                    name = request.name ?: throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name é obrigatório"),
                    description = request.description,
                )
            }

        movieListItemCrudRepository.upsertItem(list.id, movieId)
        touchList(list)
        return movieQueryRepository.getMovieListSummary(list.id)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie list not found")
    }

    @Transactional
    fun removeMovie(
        movieId: Long,
        listId: Long,
    ) {
        requireMovie(movieId)
        val list =
            movieListRepository.findById(listId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Movie list not found")
            }
        val removed = movieListItemCrudRepository.removeItem(listId, movieId)
        if (removed > 0) {
            if (list.coverMovieId == movieId) {
                movieListRepository.save(list.copy(coverMovieId = null, updatedAt = Instant.now()))
            } else {
                touchList(listId)
            }
        }
    }

    @Transactional
    fun updateOrder(
        listId: Long,
        request: MovieListOrderUpdateRequest,
    ) {
        movieListRepository.findById(listId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Movie list not found")
        }

        val items = movieListItemCrudRepository.listPositions(listId)
        if (items.isEmpty()) return

        val currentMovieIds = items.map { it.movieId }
        val requestedMovieIds = request.movieIds

        if (requestedMovieIds.size != currentMovieIds.size || requestedMovieIds.toSet() != currentMovieIds.toSet()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "movieIds inválidos para esta lista")
        }

        val currentPositionsByMovieId = items.associate { it.movieId to it.position }
        requestedMovieIds.forEachIndexed { index, movieId ->
            val desiredPosition = index + 1
            if (currentPositionsByMovieId[movieId] != desiredPosition) {
                movieListItemCrudRepository.updatePosition(listId, movieId, desiredPosition)
            }
        }
        touchList(listId)
    }

    @Transactional
    fun updateCover(
        listId: Long,
        request: MovieListCoverUpdateRequest,
    ): MovieListSummaryDto {
        val list =
            movieListRepository.findById(listId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Movie list not found")
            }

        val coverMovieId = request.coverMovieId
        if (coverMovieId != null) {
            val belongsToList = movieListItemCrudRepository.listPositions(listId).any { it.movieId == coverMovieId }
            if (!belongsToList) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "coverMovieId inválido para esta lista")
            }
        }

        movieListRepository.save(
            list.copy(
                coverMovieId = coverMovieId,
                updatedAt = Instant.now(),
            ),
        )

        return movieQueryRepository.getMovieListSummary(listId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Movie list not found")
    }

    private fun requireMovie(movieId: Long) {
        movieRepository.findById(movieId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Movie not found")
        }
    }

    private fun createList(
        name: String,
        description: String?,
    ): MovieList {
        val normalizedName = name.trim().replace("\\s+".toRegex(), " ")
        if (normalizedName.isBlank()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name é obrigatório")
        }
        val normalizedNameKey = normalizedName.lowercase()

        val normalizedDescription =
            description
                ?.trim()
                ?.replace("\\s+".toRegex(), " ")
                ?.ifBlank { null }

        movieListRepository.findByNormalizedName(normalizedNameKey)?.let { existing ->
            return existing
        }

        return movieListRepository.save(
            MovieList(
                name = normalizedName,
                normalizedName = normalizedNameKey,
                slug = SlugTextUtil.normalize(normalizedName, maxLength = 80),
                description = normalizedDescription,
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun touchList(list: MovieList) {
        touchList(list.id)
    }

    private fun touchList(listId: Long) {
        val existing =
            movieListRepository.findById(listId).orElseThrow {
                ResponseStatusException(HttpStatus.NOT_FOUND, "Movie list not found")
            }
        movieListRepository.save(existing.copy(updatedAt = Instant.now()))
    }
}
