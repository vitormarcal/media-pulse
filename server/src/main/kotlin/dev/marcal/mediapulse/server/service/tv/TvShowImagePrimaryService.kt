package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional

@Service
class TvShowImagePrimaryService(
    private val tvShowImageCrudRepository: TvShowImageCrudRepository,
) {
    @Transactional
    fun setPrimaryForShow(
        showId: Long,
        url: String,
    ) {
        tvShowImageCrudRepository.lockShowRowForPrimaryUpdate(showId)
        tvShowImageCrudRepository.clearPrimaryForShow(showId)
        tvShowImageCrudRepository.markPrimaryForShow(showId, url)
    }
}
