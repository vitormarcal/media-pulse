package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TvShowImagePrimaryService(
    private val images: TvShowImageCrudRepository,
    private val shows: TvShowRepository,
) {
    @Transactional
    fun append(
        showId: Long,
        url: String,
    ) {
        checkNotNull(images.lockShowRowForPrimaryUpdate(showId)) { "Série não encontrada." }
        images.insertIgnore(showId, url, false)
    }

    @Transactional
    fun assign(
        showId: Long,
        urls: List<String>,
        preferred: String,
        replaceCover: Boolean = false,
    ): String? {
        require(preferred in urls)
        checkNotNull(images.lockShowRowForPrimaryUpdate(showId)) { "Série não encontrada." }
        urls.distinct().forEach { images.insertIgnore(showId, it, false) }
        if (!replaceCover && (!shows.storedCover(showId).isNullOrBlank() || images.existsByShowIdAndIsPrimaryTrue(showId))) {
            return null
        }
        images.clearPrimaryForShow(showId)
        images.markPrimaryForShow(showId, preferred)
        shows.updateCover(showId, preferred)
        return preferred
    }
}
