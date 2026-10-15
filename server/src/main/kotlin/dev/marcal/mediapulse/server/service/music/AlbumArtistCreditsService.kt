package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.MusicBrainzAlbumArtistDto
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyArtist
import dev.marcal.mediapulse.server.model.music.AlbumArtistCredit
import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.repository.crud.AlbumArtistRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.slf4j.LoggerFactory
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
    private val logger = LoggerFactory.getLogger(javaClass)

    @Transactional
    fun applySpotify(
        albumId: Long,
        incoming: List<SpotifyArtist>?,
    ) {
        if (incoming.isNullOrEmpty() || incoming.any { it.id.isNullOrBlank() || it.name.isNullOrBlank() }) return
        credits.lockAlbum(albumId)
        val current = credits.findByAlbumId(albumId)
        if (current.any { it.source == AlbumArtistSource.MUSICBRAINZ_CONFIRMED }) return

        // Name uniqueness cannot safely represent distinct Spotify artists with the same canonical name.
        val homonyms = incoming.groupBy { FingerprintUtil.artistFp(requireNotNull(it.name)) }
        if (homonyms.values.any { group -> group.map { it.id }.distinct().size > 1 }) {
            logger.debug("Skipping ambiguous Spotify album artist names. albumId={}", albumId)
            return
        }
        val candidates =
            incoming.map { remote ->
                val id = requireNotNull(remote.id)
                val name = requireNotNull(remote.name)
                val fingerprint = FingerprintUtil.artistFp(name)
                val local =
                    artists.findBySpotifyId(id)
                        ?: artists.findByFingerprint(fingerprint)
                        ?: artists.findByNameAlias(fingerprint)
                SpotifyCreditCandidate(id, name, local)
            }
        if (candidates.any { it.local?.spotifyId != null && it.local.spotifyId != it.id }) {
            logger.debug("Skipping conflicting Spotify album artist identity. albumId={}", albumId)
            return
        }
        val sharedLocalIdentities = candidates.filter { it.local != null }.groupBy { requireNotNull(it.local).id }
        if (sharedLocalIdentities.values.any { group -> group.map { it.id }.distinct().size > 1 }) {
            logger.debug("Skipping Spotify artists resolving to the same local identity. albumId={}", albumId)
            return
        }
        val knownIds = candidates.mapNotNull { it.local?.id }.toSet()
        if (current.any { it.source == AlbumArtistSource.SPOTIFY && it.artistId !in knownIds }) return

        val resolvedBySpotifyId = mutableMapOf<String, Artist>()
        val resolved =
            candidates.mapIndexed { position, candidate ->
                val artist = resolvedBySpotifyId.getOrPut(candidate.id) { resolveSpotifyArtist(candidate) }
                AlbumArtistCredit(
                    albumId,
                    position,
                    artist.id,
                    candidate.name,
                    if (position < candidates.lastIndex) ", " else "",
                    AlbumArtistSource.SPOTIFY,
                )
            }
        if (current != resolved) credits.replace(albumId, resolved)
    }

    private data class SpotifyCreditCandidate(
        val id: String,
        val name: String,
        val local: Artist?,
    )

    private fun resolveSpotifyArtist(candidate: SpotifyCreditCandidate): Artist {
        val local = candidate.local
        if (local?.spotifyId == candidate.id) return local
        return artists.save(
            local?.copy(spotifyId = candidate.id, updatedAt = Instant.now())
                ?: Artist(name = candidate.name, fingerprint = FingerprintUtil.artistFp(candidate.name), spotifyId = candidate.id),
        )
    }

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
        if (credits.findByAlbumId(albumId) != resolved) {
            credits.replace(albumId, resolved)
        } else {
            // A new explicit confirmation must protect a later undo, even when names are unchanged.
            credits.bumpRevision(albumId)
        }
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
