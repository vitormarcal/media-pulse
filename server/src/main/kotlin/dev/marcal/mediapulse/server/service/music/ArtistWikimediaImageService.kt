package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbUrlRelation
import dev.marcal.mediapulse.server.integration.wikimedia.WikimediaApiClient
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import kotlinx.coroutines.CancellationException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.web.reactive.function.client.WebClientResponseException
import java.time.Instant

@Service
class ArtistWikimediaImageService(
    private val artists: ArtistRepository,
    private val wikimedia: WikimediaApiClient,
    private val storage: ImageStorageService,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    suspend fun ensureImage(
        artistId: Long,
        relations: List<MbUrlRelation>,
    ) {
        val artist = artists.findById(artistId).orElseThrow()
        if (artist.profileImageUrl != null) return
        val wikidataId = relations.asSequence().mapNotNull(::wikidataId).firstOrNull()
        if (wikidataId == null) {
            artists.save(artist.copy(wikimediaAttemptedAt = Instant.now(), wikimediaSyncError = "Artista sem vínculo Wikidata"))
            return
        }
        try {
            val fileName = wikimedia.primaryImageFile(wikidataId)
            if (fileName == null) {
                artists.save(
                    artist.copy(
                        wikidataEntityId = wikidataId,
                        wikimediaAttemptedAt = Instant.now(),
                        wikimediaSyncError = "Wikidata não informa uma imagem principal",
                    ),
                )
                return
            }
            val metadata = wikimedia.imageMetadata(fileName) ?: error("Arquivo não encontrado no Wikimedia Commons")
            val localPath =
                storage.saveImageForArtist(
                    image = wikimedia.downloadImage(metadata),
                    provider = "WIKIMEDIA",
                    artistId = artist.id,
                    fileNameHint = artist.name,
                )
            artists.save(
                artist.copy(
                    profileImageUrl = localPath,
                    wikidataEntityId = wikidataId,
                    wikimediaFileName = metadata.fileName,
                    wikimediaOriginalUrl = metadata.originalUrl,
                    wikimediaDescriptionUrl = metadata.descriptionUrl,
                    wikimediaAuthor = metadata.author,
                    wikimediaLicense = metadata.license,
                    wikimediaLicenseUrl = metadata.licenseUrl,
                    wikimediaImportedAt = Instant.now(),
                    wikimediaAttemptedAt = Instant.now(),
                    wikimediaSyncError = null,
                    updatedAt = Instant.now(),
                ),
            )
        } catch (ex: CancellationException) {
            throw ex
        } catch (ex: WebClientResponseException) {
            logger.warn(
                "Failed to enrich artist image from Wikimedia. artistId={} wikidataId={} status={}",
                artistId,
                wikidataId,
                ex.statusCode.value(),
            )
            recordFailure(artist, wikidataId)
        } catch (ex: Exception) {
            logger.warn("Failed to enrich artist image from Wikimedia. artistId={} wikidataId={}", artistId, wikidataId, ex)
            recordFailure(artist, wikidataId)
        }
    }

    private fun recordFailure(
        artist: Artist,
        wikidataId: String,
    ) {
        artists.save(
            artist.copy(
                wikidataEntityId = wikidataId,
                wikimediaAttemptedAt = Instant.now(),
                wikimediaSyncError = "Não foi possível obter a foto no Wikimedia Commons",
            ),
        )
    }

    private fun wikidataId(relation: MbUrlRelation): String? {
        val resource = relation.url?.resource ?: return null
        if (relation.type != "wikidata" && !resource.contains("wikidata.org", ignoreCase = true)) return null
        return Regex("(?:^|/)(Q\\d+)(?:$|[?#])", RegexOption.IGNORE_CASE)
            .find(resource)
            ?.groupValues
            ?.get(1)
            ?.uppercase()
    }
}
