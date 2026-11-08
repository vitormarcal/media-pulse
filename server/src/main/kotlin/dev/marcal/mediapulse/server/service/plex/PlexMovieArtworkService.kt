package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import dev.marcal.mediapulse.server.service.movie.MovieImageAssignmentService
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class PlexMovieArtworkService(
    private val plexApi: dev.marcal.mediapulse.server.integration.plex.PlexApiClient,
    private val imageStorageService: LibraryImageStorageService,
    private val imageAssignment: MovieImageAssignmentService,
) {
    data class PlexMovieImageCandidate(
        val url: String?,
        val isPoster: Boolean,
    )

    private val logger = LoggerFactory.getLogger(this::class.java)

    suspend fun ensureMovieImagesFromPlex(
        movie: Movie,
        images: List<PlexMovieImageCandidate>,
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
                val fileHint = "${movie.originalTitle}_${DigestUtils.sha1Hex(sourceUrl).take(12)}"
                val localPath =
                    imageStorageService.saveMovie(
                        image = image,
                        provider = "PLEX",
                        movieId = movie.id,
                        fileHint = fileHint,
                        title = movie.originalTitle,
                    )
                savedBySource[sourceUrl] = localPath
            }.onFailure { ex ->
                logger.warn(
                    "Failed to download/store movie image. movieId='{}' title='{}' source='{}'",
                    movie.id,
                    movie.originalTitle,
                    sourceUrl,
                    ex,
                )
            }
        }

        if (savedBySource.isEmpty()) return

        val preferred = savedBySource[preferredPrimarySource] ?: savedBySource.values.first()
        imageAssignment.assign(movie.id, savedBySource.values.toList(), preferred)
    }
}
