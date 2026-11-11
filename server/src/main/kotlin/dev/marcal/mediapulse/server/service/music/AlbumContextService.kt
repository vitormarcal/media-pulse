package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.AlbumContextDto
import dev.marcal.mediapulse.server.api.music.AlbumContextPreviewDto
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.wikimedia.AlbumWikipediaClient
import dev.marcal.mediapulse.server.repository.AlbumContextRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.util.TxUtil
import kotlinx.coroutines.CancellationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class AlbumContextService(
    private val albums: AlbumRepository,
    private val musicBrainz: MusicBrainzApiClient,
    private val wikipedia: AlbumWikipediaClient,
    private val contexts: AlbumContextRepository,
    private val tx: TxUtil,
) {
    private data class Pending(
        val albumId: Long,
        val preview: AlbumContextPreviewDto,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    suspend fun preview(albumId: Long): AlbumContextPreviewDto {
        val album = albums.findById(albumId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
        val mbid =
            album.musicbrainzReleaseGroupId
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Vincule o álbum ao MusicBrainz primeiro.")
        val candidate =
            try {
                musicBrainz.getReleaseGroup(mbid)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_GATEWAY, "Não foi possível consultar o MusicBrainz.")
            }
        val intro =
            try {
                wikipedia.introduction(candidate.relations)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Não foi possível consultar a Wikipedia. O conteúdo salvo foi preservado.",
                )
            }
        val current = contexts.find(albumId)
        val now = Instant.now()
        val proposed =
            AlbumContextDto(
                releaseGroupMbid = mbid,
                primaryType = candidate.primaryType,
                secondaryTypes = candidate.secondaryTypes,
                firstReleaseDate = candidate.firstReleaseDate,
                links = wikipedia.externalLinks(candidate.relations, mbid),
                introduction = intro ?: current?.takeIf { it.releaseGroupMbid == mbid }?.introduction,
                fetchedAt = now,
            )
        val result = AlbumContextPreviewDto(UUID.randomUUID().toString(), current, proposed, now.plusSeconds(900))
        synchronized(pending) {
            pending.entries.removeIf {
                it.value.preview.expiresAt
                    .isBefore(now)
            }
            if (pending.size >= 100) pending.keys.firstOrNull()?.let { pending.remove(it) }
            pending[result.token] = Pending(albumId, result)
        }
        return result
    }

    fun apply(
        albumId: Long,
        token: String,
    ): AlbumContextDto {
        val snapshot =
            pending[token]?.takeIf { it.albumId == albumId && it.preview.expiresAt.isAfter(Instant.now()) }
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Prévia expirada. Consulte os dados novamente.")
        val result =
            tx.inTx {
                val mbid = contexts.lockAlbum(albumId)
                if (mbid != snapshot.preview.proposed.releaseGroupMbid || contexts.find(albumId) != snapshot.preview.current) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "O álbum mudou. Consulte uma nova prévia.")
                }
                contexts.save(albumId, snapshot.preview.proposed)
                snapshot.preview.proposed
            }
        pending.remove(token, snapshot)
        return result
    }
}
