package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import dev.marcal.mediapulse.server.service.tv.TvShowImagePrimaryService
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PlexShowArtworkService(
    private val plexApi: dev.marcal.mediapulse.server.integration.plex.PlexApiClient,
    private val imageStorageService: ImageStorageService,
    private val tvShowImageCrudRepository: TvShowImageCrudRepository,
    private val tvShowImagePrimaryService: TvShowImagePrimaryService,
    private val tvShowRepository: TvShowRepository,
) {
    data class PlexShowImageCandidate(
        val url: String?,
        val isPoster: Boolean,
    )

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun ensureShowImagesFromPlex(
        show: TvShow,
        images: List<PlexShowImageCandidate>,
        fallbackThumbPath: String?,
    ) {
        val sourceCandidates =
            buildList {
                images.forEach { candidate ->
                    val clean = candidate.url?.trim()?.ifBlank { null } ?: return@forEach
                    add(clean to candidate.isPoster)
                }
                val thumb = fallbackThumbPath?.trim()?.ifBlank { null }
                if (thumb != null) add(thumb to false)
            }.distinctBy { it.first }

        if (sourceCandidates.isEmpty()) return

        val preferredPrimarySource = sourceCandidates.firstOrNull { it.second }?.first ?: sourceCandidates.first().first
        val savedBySource = linkedMapOf<String, String>()

        for ((sourceUrl, _) in sourceCandidates) {
            runCatching {
                val image = plexApi.downloadImageContent(sourceUrl)
                val fileHint = "${show.originalTitle}_${DigestUtils.sha1Hex(sourceUrl).take(12)}"
                val localPath =
                    imageStorageService.saveImageForTvShow(
                        image = image,
                        provider = "PLEX",
                        showId = show.id,
                        fileNameHint = fileHint,
                    )
                savedBySource[sourceUrl] = localPath
            }.onFailure { ex ->
                logger.warn(
                    "Failed to download/store tv show image. showId='{}' title='{}' source='{}'",
                    show.id,
                    show.originalTitle,
                    sourceUrl,
                    ex,
                )
            }
        }

        if (savedBySource.isEmpty()) return

        savedBySource.values.forEach { localPath ->
            tvShowImageCrudRepository.insertIgnore(
                showId = show.id,
                url = localPath,
                isPrimary = false,
            )
        }

        val primaryLocalPath = savedBySource[preferredPrimarySource] ?: savedBySource.values.first()

        tvShowImagePrimaryService.setPrimaryForShow(show.id, primaryLocalPath)

        if (show.coverUrl != primaryLocalPath) {
            tvShowRepository.save(
                show.copy(
                    coverUrl = primaryLocalPath,
                    updatedAt = Instant.now(),
                ),
            )
        }
    }
}
