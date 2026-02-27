package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class MovieImagePrimaryService(
    private val movieImageCrudRepository: MovieImageCrudRepository,
) {
    @Transactional
    fun setPrimaryForMovie(
        movieId: Long,
        url: String,
    ) {
        movieImageCrudRepository.lockMovieRowForPrimaryUpdate(movieId)
        movieImageCrudRepository.clearPrimaryForMovie(movieId)
        movieImageCrudRepository.markPrimaryForMovie(movieId, url)
    }
}
