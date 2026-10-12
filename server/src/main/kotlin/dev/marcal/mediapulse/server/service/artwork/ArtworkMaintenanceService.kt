package dev.marcal.mediapulse.server.service.artwork

import dev.marcal.mediapulse.server.api.artwork.ArtworkRequest
import org.springframework.stereotype.Service

@Service
class ArtworkMaintenanceService(
    private val works: ArtworkService,
    private val images: ArtworkImageService,
    private val sync: ArtworkMovementSyncService,
) {
    // Domain services commit before calling the remote service.
    fun update(
        id: Long,
        request: ArtworkRequest,
    ) = works.update(id, request).let {
        if (it.artwork.movementsSyncPending) sync.synchronize(id)
        works.details(id)
    }

    fun attach(
        id: Long,
        url: String,
        sourceUrl: String?,
        caption: String?,
    ) = images.attach(id, url, sourceUrl, caption).also {
        if (dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
                .assetId(url) != null
        ) {
            sync.synchronize(id)
        }
    }

    fun retry(id: Long) =
        works.work(id).let {
            sync.synchronize(id)
            works.details(id)
        }
}
