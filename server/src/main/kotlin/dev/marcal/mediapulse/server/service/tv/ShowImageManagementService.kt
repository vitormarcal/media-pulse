package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowImageManagementResponse
import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException

@Service
class ShowImageManagementService(
    private val shows: TvShowRepository,
    private val images: TvShowImageCrudRepository,
) {
    @Transactional
    fun update(
        showId: Long,
        url: String,
        remove: Boolean,
    ): ShowImageManagementResponse {
        if (images.lockShowRowForPrimaryUpdate(showId) == null) missing()
        val currentCover = shows.storedCover(showId)
        val gallery = images.findByShowIdOrderByIsPrimaryDescIdDesc(showId)
        if (url.isBlank() || (currentCover != url && gallery.none { it.url == url })) missing()

        val cover =
            if (remove) {
                images.removeForShow(showId, url)
                if (currentCover == url || gallery.any { it.url == url && it.isPrimary }) {
                    gallery.firstOrNull { it.url != url }?.url
                } else {
                    currentCover
                }
            } else {
                // Older covers can exist without a gallery association.
                images.insertIgnore(showId, url, false)
                url
            }
        images.clearPrimaryForShow(showId)
        if (cover != null) images.markPrimaryForShow(showId, cover)
        shows.updateCover(showId, cover)
        return ShowImageManagementResponse(cover)
    }

    private fun missing(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Série ou imagem não encontrada.")
}
