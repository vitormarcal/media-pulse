package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class MovieImageAssignmentService(
    private val movies: MovieRepository,
    private val images: MovieImageCrudRepository,
) {
    @Transactional
    fun assign(
        movieId: Long,
        urls: List<String>,
        preferred: String,
        replaceCover: Boolean = false,
    ): String? {
        if (images.lockMovieRowForPrimaryUpdate(movieId) ==
            null
        ) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Filme não encontrado.")
        }
        val currentCover = movies.storedCover(movieId)
        urls.distinct().forEach { images.insertIgnore(movieId, it, false) }
        if (!replaceCover && (!currentCover.isNullOrBlank() || images.existsByMovieIdAndIsPrimaryTrue(movieId))) return null
        require(preferred in urls)
        images.clearPrimaryForMovie(movieId)
        images.markPrimaryForMovie(movieId, preferred)
        movies.updateCover(movieId, preferred)
        return preferred
    }
}
