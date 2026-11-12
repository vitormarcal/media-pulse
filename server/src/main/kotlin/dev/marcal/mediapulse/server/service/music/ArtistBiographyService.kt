package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.ArtistBiographyDto
import dev.marcal.mediapulse.server.api.music.ArtistBiographyPreviewDto
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.wikimedia.WikipediaIntroductionClient
import dev.marcal.mediapulse.server.repository.ArtistBiographyRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.util.TxUtil
import kotlinx.coroutines.CancellationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

@Service
class ArtistBiographyService(
    private val artists: ArtistRepository,
    private val musicBrainz: MusicBrainzApiClient,
    private val wikipedia: WikipediaIntroductionClient,
    private val biographies: ArtistBiographyRepository,
    private val tx: TxUtil,
) {
    private data class Pending(
        val artistId: Long,
        val preview: ArtistBiographyPreviewDto,
    )

    private val pending = ConcurrentHashMap<String, Pending>()

    suspend fun preview(artistId: Long): ArtistBiographyPreviewDto {
        val artist = artists.findById(artistId).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND) }
        val mbid =
            artist.musicbrainzArtistId ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Vincule o artista ao MusicBrainz primeiro.")
        val introduction =
            try {
                wikipedia.introduction(musicBrainz.getArtist(mbid).relations)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                throw ResponseStatusException(
                    HttpStatus.BAD_GATEWAY,
                    "Não foi possível consultar a biografia. O texto salvo foi preservado.",
                )
            }
        val current = biographies.find(artistId)
        if (introduction == null) return ArtistBiographyPreviewDto(null, current, null, null)
        val now = Instant.now()
        val result =
            ArtistBiographyPreviewDto(
                UUID.randomUUID().toString(),
                current,
                ArtistBiographyDto(mbid, introduction, now),
                now.plusSeconds(900),
            )
        synchronized(pending) {
            pending.entries.removeIf {
                it.value.preview.expiresAt!!
                    .isBefore(now)
            }
            if (pending.size >= 100) pending.keys.firstOrNull()?.let { pending.remove(it) }
            pending[result.token!!] = Pending(artistId, result)
        }
        return result
    }

    fun apply(
        artistId: Long,
        token: String,
    ): ArtistBiographyDto {
        val snapshot =
            pending[token]?.takeIf { it.artistId == artistId && it.preview.expiresAt!!.isAfter(Instant.now()) }
                ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Prévia expirada. Consulte novamente.")
        val result =
            tx.inTx {
                val proposed = snapshot.preview.proposed!!
                if (biographies.lockArtist(artistId) != proposed.artistMbid || biographies.find(artistId) != snapshot.preview.current) {
                    throw ResponseStatusException(HttpStatus.CONFLICT, "O artista mudou. Consulte uma nova prévia.")
                }
                biographies.save(artistId, proposed)
                proposed
            }
        pending.remove(token, snapshot)
        return result
    }
}
