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
        // 1) por MBID
        musicbrainzId?.let {
            extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, it)?.let { ext ->
                if (ext.entityType == EntityType.ARTIST) return artistRepo.findById(ext.entityId).get()
            }
        }
        // 2) por provider
        plexGuid?.let {
            extRepo.findByProviderAndExternalId(Provider.PLEX, it)?.let { ext ->
                if (ext.entityType == EntityType.ARTIST) return artistRepo.findById(ext.entityId).get()
            }
        }
        spotifyId?.let {
            extRepo.findByProviderAndExternalId(Provider.SPOTIFY, it)?.let { ext ->
                if (ext.entityType == EntityType.ARTIST) return artistRepo.findById(ext.entityId).get()
            }
        }
        // 3) por fingerprint
        val fp = FingerprintUtil.artistFp(name)
        val existing = artistRepo.findByFingerprint(fp)
        val artist = existing ?: artistRepo.save(Artist(name = name, fingerprint = fp))

        // vincula externos se vieram
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
        // por MBID
        musicbrainzId?.let {
            extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, it)?.let { ext ->
                if (ext.entityType == EntityType.ALBUM) return albumRepo.findById(ext.entityId).get()
            }
        }
        // por provider
        plexGuid?.let {
            extRepo.findByProviderAndExternalId(Provider.PLEX, it)?.let { ext ->
                if (ext.entityType == EntityType.ALBUM) return albumRepo.findById(ext.entityId).get()
            }
        }
        spotifyId?.let {
            extRepo.findByProviderAndExternalId(Provider.SPOTIFY, it)?.let { ext ->
                if (ext.entityType == EntityType.ALBUM) return albumRepo.findById(ext.entityId).get()
            }
        }
        // fallback por chave natural
        albumRepo.findByArtistIdAndTitleAndYear(artist.id, title, year)?.let { return it }

        val fp = FingerprintUtil.albumFp(title, artist.id, year)
        val existing = albumRepo.findByFingerprint(fp)
        val album =
            existing ?: albumRepo.save(
                Album(artistId = artist.id, title = title, year = year, coverUrl = coverUrl, fingerprint = fp),
            )

        // vincula externos
        musicbrainzId?.let { safeLink(EntityType.ALBUM, album.id, Provider.MUSICBRAINZ, it) }
        plexGuid?.let { safeLink(EntityType.ALBUM, album.id, Provider.PLEX, it) }
        spotifyId?.let { safeLink(EntityType.ALBUM, album.id, Provider.SPOTIFY, it) }

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
        // por MBID
        musicbrainzId?.let {
            extRepo.findByProviderAndExternalId(Provider.MUSICBRAINZ, it)?.let { ext ->
                if (ext.entityType == EntityType.TRACK) return trackRepo.findById(ext.entityId).get()
            }
        }
        // por provider
        plexGuid?.let {
            extRepo.findByProviderAndExternalId(Provider.PLEX, it)?.let { ext ->
                if (ext.entityType == EntityType.TRACK) return trackRepo.findById(ext.entityId).get()
            }
        }
        spotifyId?.let {
            extRepo.findByProviderAndExternalId(Provider.SPOTIFY, it)?.let { ext ->
                if (ext.entityType == EntityType.TRACK) return trackRepo.findById(ext.entityId).get()
            }
        }

        val fp = FingerprintUtil.trackFp(title, album.id, discNumber, trackNumber, durationMs)
        val existing = trackRepo.findByFingerprint(fp)
        val track =
            existing ?: trackRepo.save(
                Track(
                    albumId = album.id,
                    title = title,
                    trackNumber = trackNumber,
                    discNumber = discNumber,
                    durationMs = durationMs,
                    fingerprint = fp,
                ),
            )

        // vincula externos
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
