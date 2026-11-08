package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieImageManagementResponse
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class MovieImageManagementService(
    private val movies: MovieRepository,
    private val images: MovieImageCrudRepository,
) {
    @Transactional
    fun update(
        movieId: Long,
        url: String,
        remove: Boolean,
    ): MovieImageManagementResponse {
        if (images.lockMovieRowForPrimaryUpdate(movieId) == null) missing()
        val movie = movies.findById(movieId).orElseThrow { missing() }
        val gallery = images.findByMovieIdOrderByIsPrimaryDescIdDesc(movieId)
        if (url.isBlank() || (movie.coverUrl != url && gallery.none { it.url == url })) missing()

        val cover =
            if (remove) {
                images.removeForMovie(movieId, url)
                if (movie.coverUrl == url || gallery.any { it.url == url && it.isPrimary }) {
                    gallery.firstOrNull { it.url != url }?.url
                } else {
                    movie.coverUrl
                }
            } else {
                // Older covers can exist without a gallery association.
                images.insertIgnore(movieId, url, false)
                url
            }
        images.clearPrimaryForMovie(movieId)
        if (cover != null) images.markPrimaryForMovie(movieId, cover)
        movies.updateCover(movieId, cover)
        return MovieImageManagementResponse(cover)
    }

    private fun missing(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Filme ou imagem não encontrado.")
}
