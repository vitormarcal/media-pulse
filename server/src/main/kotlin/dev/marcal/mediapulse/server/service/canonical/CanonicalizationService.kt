package dev.marcal.mediapulse.server.service.canonical

import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.model.music.Track
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumTrackCrudRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.TrackRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.Instant

@Service
class CanonicalizationService(
    private val artistRepo: ArtistRepository,
    private val albumRepo: AlbumRepository,
    private val trackRepo: TrackRepository,
    private val extRepo: ExternalIdentifierRepository,
    private val albumTrackRepo: AlbumTrackCrudRepository,
) {
    @Transactional
    fun ensureArtist(
        name: String,
        musicbrainzId: String? = null,
        spotifyId: String? = null,
    ): Artist {
        fun findByExternal(
            provider: Provider,
            externalId: String,
        ): Artist? {
            val ext = extRepo.findByProviderAndExternalId(provider, externalId) ?: return null
            if (ext.entityType != EntityType.ARTIST) return null
            return artistRepo.findById(ext.entityId).orElse(null)
        }

        val found =
            musicbrainzId?.let { findByExternal(Provider.MUSICBRAINZ, it) }
                ?: spotifyId?.let { findByExternal(Provider.SPOTIFY, it) }
                ?: artistRepo.findByFingerprint(FingerprintUtil.artistFp(name))

        val artist =
            found ?: artistRepo.save(
                Artist(
                    name = name,
                    fingerprint = FingerprintUtil.artistFp(name),
                ),
            )

        musicbrainzId?.let { safeLink(EntityType.ARTIST, artist.id, Provider.MUSICBRAINZ, it) }
        spotifyId?.let { safeLink(EntityType.ARTIST, artist.id, Provider.SPOTIFY, it) }

        return artist
    }

    @Transactional
    fun ensureAlbum(
        artist: Artist,
        title: String,
        year: Int?,
        coverUrl: String?,
        musicbrainzId: String? = null,
        spotifyId: String? = null,
    ): Album {
        fun findByExternal(
            provider: Provider,
            externalId: String,
        ): Album? {
            val ext = extRepo.findByProviderAndExternalId(provider, externalId) ?: return null
            if (ext.entityType != EntityType.ALBUM) return null
            return albumRepo.findById(ext.entityId).orElse(null)
        }

        val found =
            musicbrainzId?.let { findByExternal(Provider.MUSICBRAINZ, it) }
                ?: spotifyId?.let { findByExternal(Provider.SPOTIFY, it) }
                ?: albumRepo.findByArtistIdAndTitleAndYear(artist.id, title, year)
                ?: run {
                    if (year == null) {
                        val candidates = albumRepo.findAllByArtistIdAndTitle(artist.id, title)
                        if (candidates.size == 1) candidates.first() else null
                    } else {
                        null
                    }
                }
                ?: albumRepo.findByFingerprint(FingerprintUtil.albumFp(title, artist.id, year))

        val album0 =
            found ?: albumRepo.save(
                Album(
                    artistId = artist.id,
                    title = title,
                    year = year,
                    coverUrl = coverUrl,
                    fingerprint = FingerprintUtil.albumFp(title, artist.id, year),
                ),
            )

        val album =
            if (year != null && album0.year == null) {
                albumRepo.save(album0.copy(year = year, updatedAt = Instant.now()))
            } else {
                album0
            }

        musicbrainzId?.let { safeLink(EntityType.ALBUM, album.id, Provider.MUSICBRAINZ, it) }
        spotifyId?.let { safeLink(EntityType.ALBUM, album.id, Provider.SPOTIFY, it) }

        return album
    }

    /**
     * Track is a recording (artist-scoped), not "track in album".
     * Identity priority: MBID > Spotify ID > fingerprint(title+artist+duration?).
     */
    @Transactional
    fun ensureTrack(
        artist: Artist,
        title: String,
        durationMs: Int?,
        musicbrainzId: String? = null,
        spotifyId: String? = null,
    ): Track {
        fun findByExternal(
            provider: Provider,
            externalId: String,
        ): Track? {
            val ext = extRepo.findByProviderAndExternalId(provider, externalId) ?: return null
            if (ext.entityType != EntityType.TRACK) return null
            return trackRepo.findById(ext.entityId).orElse(null)
        }

        val found =
            musicbrainzId?.let { findByExternal(Provider.MUSICBRAINZ, it) }
                ?: spotifyId?.let { findByExternal(Provider.SPOTIFY, it) }
                ?: run {
                    val fp = FingerprintUtil.trackFp(title = title, artistId = artist.id)
                    trackRepo.findByFingerprint(fp)
                }

        val track =
            found ?: run {
                val fp = FingerprintUtil.trackFp(title = title, artistId = artist.id)
                trackRepo.save(
                    Track(
                        artistId = artist.id,
                        title = title,
                        durationMs = durationMs,
                        fingerprint = fp,
                    ),
                )
            }

        musicbrainzId?.let { safeLink(EntityType.TRACK, track.id, Provider.MUSICBRAINZ, it) }
        spotifyId?.let { safeLink(EntityType.TRACK, track.id, Provider.SPOTIFY, it) }

        return track
    }

    /**
     * Links track to an album edition, capturing position when known.
     * Idempotent. Never forces position when disc/track is unknown.
     */
    @Transactional
    fun linkTrackToAlbum(
        album: Album,
        track: Track,
        discNumber: Int?,
        trackNumber: Int?,
    ) {
        if (discNumber == null || trackNumber == null) {
            albumTrackRepo.insertIgnoreByPk(
                albumId = album.id,
                trackId = track.id,
                discNumber = null,
                trackNumber = null,
            )
            return
        }

        albumTrackRepo.upsertByPosition(
            albumId = album.id,
            trackId = track.id,
            discNumber = discNumber,
            trackNumber = trackNumber,
        )
    }

    @Transactional
    fun updateAlbumCoverIfEmpty(
        albumId: Long,
        localCoverPath: String,
    ): Album {
        val album = albumRepo.findById(albumId).orElseThrow()
        if (album.coverUrl == null) {
            return albumRepo.save(album.copy(coverUrl = localCoverPath, updatedAt = Instant.now()))
        }
        return album
    }

    private fun safeLink(
        type: EntityType,
        id: Long,
        provider: Provider,
        externalId: String,
    ) {
        if (extRepo.findByProviderAndExternalId(provider, externalId) == null) {
            extRepo.save(
                ExternalIdentifier(
                    entityType = type,
                    entityId = id,
                    provider = provider,
                    externalId = externalId,
                ),
            )
        }
    }
}
