package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.model.movie.Movie
import dev.marcal.mediapulse.server.repository.crud.MovieImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import org.apache.commons.codec.digest.DigestUtils
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.Instant

@Service
class PlexMovieArtworkService(
    private val plexApi: dev.marcal.mediapulse.server.integration.plex.PlexApiClient,
    private val imageStorageService: ImageStorageService,
    private val movieImageCrudRepository: MovieImageCrudRepository,
    private val movieRepository: MovieRepository,
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
                    imageStorageService.saveImageForMovie(
                        image = image,
                        provider = "PLEX",
                        movieId = movie.id,
                        fileNameHint = fileHint,
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

        savedBySource.values.forEach { localPath ->
            movieImageCrudRepository.insertIgnore(
                movieId = movie.id,
                url = localPath,
                isPrimary = false,
            )
        }

        val primaryLocalPath = savedBySource[preferredPrimarySource] ?: savedBySource.values.first()

        movieImageCrudRepository.setPrimaryForMovie(movie.id, primaryLocalPath)

        if (movie.coverUrl != primaryLocalPath) {
            movieRepository.save(
                movie.copy(
                    coverUrl = primaryLocalPath,
                    updatedAt = Instant.now(),
                ),
            )
        }
    }
}
