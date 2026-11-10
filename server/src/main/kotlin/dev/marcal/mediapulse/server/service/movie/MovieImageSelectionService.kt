package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionRequest
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.service.image.TmdbImageSelectionService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class MovieImageSelectionService(
    private val entities: MovieRepository,
    private val images: MovieImageCrudRepository,
    private val selection: TmdbImageSelectionService,
    private val assignment: MovieImageAssignmentService,
) {
    fun candidates(id: Long) = selection.candidates(subject(id))

    fun save(
        id: Long,
        request: TmdbImageSelectionRequest,
    ) = selection.save(subject(id), request) { url -> assignment.append(id, url) }

    fun lookup(
        id: Long,
        request: TmdbImageSelectionRequest,
    ) = selection.lookup(subject(id), request) {
        images.findByMovieIdOrderByIsPrimaryDescIdDesc(id).map { it.url } + listOfNotNull(entities.storedCover(id))
    }

    private fun subject(id: Long): TmdbImageSelectionService.Subject {
        val entity = entities.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Filme não encontrado.") }
        return TmdbImageSelectionService.Subject(TmdbImageSelectionService.Kind.MOVIE, id, entity.originalTitle, entity.tmdbId)
    }
}
