package dev.marcal.mediapulse.server.service.canonical

import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.model.music.Track
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
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
) {
    @Transactional
    fun ensureArtist(
        name: String,
        musicbrainzId: String? = null,
        plexGuid: String? = null,
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

        val found: Artist? =
            musicbrainzId?.let { findByExternal(Provider.MUSICBRAINZ, it) }
                ?: plexGuid?.let { findByExternal(Provider.PLEX, it) }
                ?: spotifyId?.let { findByExternal(Provider.SPOTIFY, it) }
                ?: run {
                    val fp = FingerprintUtil.artistFp(name)
                    artistRepo.findByFingerprint(fp)
                }

        val artist =
            found ?: run {
                val fp = FingerprintUtil.artistFp(name)
                artistRepo.save(Artist(name = name, fingerprint = fp))
            }

        musicbrainzId?.let { safeLink(EntityType.ARTIST, artist.id, Provider.MUSICBRAINZ, it) }
        plexGuid?.let { safeLink(EntityType.ARTIST, artist.id, Provider.PLEX, it) }
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
        plexGuid: String? = null,
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

        val found: Album? =
            musicbrainzId?.let { findByExternal(Provider.MUSICBRAINZ, it) }
                ?: plexGuid?.let { findByExternal(Provider.PLEX, it) }
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
                ?: run {
                    val fp = FingerprintUtil.albumFp(title, artist.id, year)
                    albumRepo.findByFingerprint(fp)
                }

        val album =
            found ?: run {
                val fp = FingerprintUtil.albumFp(title, artist.id, year)
                albumRepo.save(
                    Album(
                        artistId = artist.id,
                        title = title,
                        year = year,
                        coverUrl = coverUrl,
                        fingerprint = fp,
                    ),
                )
            }

        if (year != null && album.year == null) {
            albumRepo.save(album.copy(year = year, updatedAt = Instant.now()))
        }

        musicbrainzId?.let { safeLink(EntityType.ALBUM, album.id, Provider.MUSICBRAINZ, it) }
        plexGuid?.let { safeLink(EntityType.ALBUM, album.id, Provider.PLEX, it) }
        spotifyId?.let { safeLink(EntityType.ALBUM, album.id, Provider.SPOTIFY, it) }

        return album
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

    @Transactional
    fun ensureTrack(
        album: Album,
        title: String,
        trackNumber: Int?,
        discNumber: Int?,
        durationMs: Int?,
        musicbrainzId: String? = null,
        plexGuid: String? = null,
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

        val found: Track? =
            musicbrainzId?.let { findByExternal(Provider.MUSICBRAINZ, it) }
                ?: plexGuid?.let { findByExternal(Provider.PLEX, it) }
                ?: spotifyId?.let { findByExternal(Provider.SPOTIFY, it) }
                ?: run {
                    if (discNumber != null && trackNumber != null) {
                        trackRepo.findByAlbumIdAndDiscNumberAndTrackNumber(album.id, discNumber, trackNumber)
                    } else {
                        null
                    }
                }
                ?: run {
                    if (discNumber == null && trackNumber == null) {
                        val candidates = trackRepo.findAllByAlbumIdAndTitle(album.id, title)
                        if (candidates.size == 1) candidates.first() else null
                    } else {
                        null
                    }
                }
                ?: run {
                    val fp = FingerprintUtil.trackFp(title, album.id, discNumber, trackNumber)
                    trackRepo.findByFingerprint(fp)
                }

        val track =
            found ?: run {
                val fp = FingerprintUtil.trackFp(title, album.id, discNumber, trackNumber)
                trackRepo.save(
                    Track(
                        albumId = album.id,
                        title = title,
                        trackNumber = trackNumber,
                        discNumber = discNumber,
                        durationMs = durationMs,
                        fingerprint = fp,
                    ),
                )
            }

        musicbrainzId?.let { safeLink(EntityType.TRACK, track.id, Provider.MUSICBRAINZ, it) }
        plexGuid?.let { safeLink(EntityType.TRACK, track.id, Provider.PLEX, it) }
        spotifyId?.let { safeLink(EntityType.TRACK, track.id, Provider.SPOTIFY, it) }

        return track
    }

    private fun safeLink(
        type: EntityType,
        id: Long,
        provider: Provider,
        externalId: String,
    ) {
        if (extRepo.findByProviderAndExternalId(provider, externalId) == null) {
            extRepo.save(ExternalIdentifier(entityType = type, entityId = id, provider = provider, externalId = externalId))
        }
    }
}
