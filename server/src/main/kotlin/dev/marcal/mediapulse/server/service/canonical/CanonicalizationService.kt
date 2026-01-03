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
import dev.marcal.mediapulse.server.util.TitleKeyUtil
import org.springframework.dao.DataIntegrityViolationException
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

        val titleKey = TitleKeyUtil.albumTitleKey(title).ifBlank { "unknown" }
        val fp = FingerprintUtil.albumFp(titleKey, artist.id)

        fun pickBestForNullYear(): Album? =
            albumRepo.findFirstByArtistIdAndTitleKeyAndYearIsNotNullOrderByYearAscIdAsc(artist.id, titleKey)
                ?: albumRepo.findFirstByArtistIdAndTitleKeyAndYearIsNullOrderByIdAsc(artist.id, titleKey)

        val byExternal =
            musicbrainzId?.let { findByExternal(Provider.MUSICBRAINZ, it) }
                ?: spotifyId?.let { findByExternal(Provider.SPOTIFY, it) }

        val byExactYear =
            if (byExternal == null && year != null) {
                albumRepo.findByArtistIdAndTitleKeyAndYear(artist.id, titleKey, year)
            } else {
                null
            }

        val byNullYearPolicy =
            if (byExternal == null && byExactYear == null && year == null) {
                pickBestForNullYear()
            } else {
                null
            }

        val found = byExternal ?: byExactYear ?: byNullYearPolicy ?: albumRepo.findByFingerprint(fp)

        val created =
            found ?: albumRepo.save(
                Album(
                    artistId = artist.id,
                    title = title,
                    titleKey = titleKey,
                    year = year,
                    coverUrl = coverUrl,
                    fingerprint = fp,
                ),
            )

        val album =
            if (year != null && created.year == null) {
                try {
                    albumRepo.promoteNullYear(created.id, year)
                    albumRepo.findById(created.id).orElseThrow()
                } catch (ex: DataIntegrityViolationException) {
                    // alguém já tem (artist,titleKey,year). Então use o correto.
                    albumRepo.findByArtistIdAndTitleKeyAndYear(artist.id, titleKey, year)
                        ?: throw ex
                }
            } else {
                created
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
                    val fp = FingerprintUtil.trackFp(title = title, artistId = artist.id, durationMs = durationMs)
                    trackRepo.findByFingerprint(fp)
                }

        val track =
            found ?: run {
                val fp = FingerprintUtil.trackFp(title = title, artistId = artist.id, durationMs = durationMs)
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
     * Idempotent.
     */
    @Transactional
    fun linkTrackToAlbum(
        album: Album,
        track: Track,
        discNumber: Int?,
        trackNumber: Int?,
    ) {
        if (discNumber != null && trackNumber != null) {
            albumTrackRepo.upsertByPosition(
                lockKey = album.id,
                albumId = album.id,
                trackId = track.id,
                discNumber = discNumber,
                trackNumber = trackNumber,
            )
        } else {
            albumTrackRepo.insertIgnoreByPk(
                albumId = album.id,
                trackId = track.id,
                discNumber = discNumber,
                trackNumber = trackNumber,
            )
        }
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
