package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.MusicBrainzAlbumArtistDto
import dev.marcal.mediapulse.server.model.music.AlbumArtistCredit
import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.repository.crud.AlbumArtistRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class AlbumArtistCreditsService(
    private val credits: AlbumArtistRepository,
    private val artists: ArtistRepository,
) {
    @Transactional
    fun applyMusicBrainz(
        albumId: Long,
        incoming: List<MusicBrainzAlbumArtistDto>,
    ) {
        // An incomplete response must not erase known credits.
        if (incoming.isEmpty()) return
        if (incoming.any { it.artistMbid.isBlank() || it.artistName.isBlank() || it.creditedName.isBlank() }) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "MusicBrainz returned incomplete artist credits")
        }
        credits.lockAlbum(albumId)
        val resolved =
            incoming.mapIndexed { position, credit ->
                val artist = resolveArtist(credit)
                AlbumArtistCredit(
                    albumId,
                    position,
                    artist.id,
                    credit.creditedName,
                    credit.joinPhrase,
                    AlbumArtistSource.MUSICBRAINZ_CONFIRMED,
                )
            }
        if (credits.findByAlbumId(albumId) != resolved) credits.replace(albumId, resolved)
    }

    private fun resolveArtist(credit: MusicBrainzAlbumArtistDto): Artist {
        artists.findByMusicbrainzArtistId(credit.artistMbid)?.let { return it }
        val fingerprint = FingerprintUtil.artistFp(credit.artistName)
        val local = artists.findByFingerprint(fingerprint)
        if (local?.musicbrainzArtistId != null && local.musicbrainzArtistId != credit.artistMbid) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Local artist name is linked to another MusicBrainz identity")
        }
        return artists.save(
            local?.copy(musicbrainzArtistId = credit.artistMbid, updatedAt = Instant.now())
                ?: Artist(name = credit.artistName, fingerprint = fingerprint, musicbrainzArtistId = credit.artistMbid),
        )
    }
}
