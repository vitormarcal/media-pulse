package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.image.TmdbImageSelectionRequest
import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.service.image.TmdbImageSelectionService
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ShowImageSelectionService(
    private val entities: TvShowRepository,
    private val images: TvShowImageCrudRepository,
    private val selection: TmdbImageSelectionService,
    private val assignment: TvShowImagePrimaryService,
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
        images.findByShowIdOrderByIsPrimaryDescIdDesc(id).map { it.url } + listOfNotNull(entities.storedCover(id))
    }

    private fun subject(id: Long): TmdbImageSelectionService.Subject {
        val entity = entities.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Série não encontrada.") }
        return TmdbImageSelectionService.Subject(TmdbImageSelectionService.Kind.SHOW, id, entity.originalTitle, entity.tmdbId)
    }
}
