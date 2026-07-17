package dev.marcal.mediapulse.server.service.musicbrainz

import dev.marcal.mediapulse.server.api.music.MusicBrainzAlbumCandidateDto
import dev.marcal.mediapulse.server.api.music.MusicBrainzAlbumPreviewResponse
import dev.marcal.mediapulse.server.api.music.MusicBrainzArtistCandidateDto
import dev.marcal.mediapulse.server.api.music.MusicBrainzArtistCreateResult
import dev.marcal.mediapulse.server.api.music.MusicBrainzDiscographyImportResult
import dev.marcal.mediapulse.server.api.music.MusicBrainzDiscographyItemDto
import dev.marcal.mediapulse.server.api.music.MusicBrainzDiscographyPreviewResponse
import dev.marcal.mediapulse.server.api.music.MusicBrainzDiscographyStatus
import dev.marcal.mediapulse.server.api.music.MusicBrainzEnrichmentResult
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalEntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.service.music.AlbumTermsService
import dev.marcal.mediapulse.server.util.FingerprintUtil
import dev.marcal.mediapulse.server.util.TitleKeyUtil
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class MusicBrainzPageEnrichmentService(
    private val client: MusicBrainzApiClient,
    private val albums: AlbumRepository,
    private val artists: ArtistRepository,
    private val externalIds: ExternalIdentifierRepository,
    private val albumTermsService: AlbumTermsService,
) {
    suspend fun searchNewArtist(query: String): List<MusicBrainzArtistCandidateDto> {
        val normalized = query.trim()
        if (normalized.length < 2) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Artist query must have at least 2 characters")
        }
        return client.searchArtists(normalized).map { it.toDto() }
    }

    suspend fun createArtist(artistMbid: String): MusicBrainzArtistCreateResult {
        val remote = client.getArtist(artistMbid)
        val linked = externalIds.findByProviderAndExternalId(Provider.MUSICBRAINZ, remote.id)
        if (linked != null && linked.entityType != EntityType.ARTIST) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "MusicBrainz identifier is linked to another local entity")
        }
        val linkedArtist = linked?.let { artists.findById(it.entityId).orElse(null) }
        val sameName = artists.findByFingerprint(FingerprintUtil.artistFp(remote.name))
        val created = linkedArtist == null && sameName == null
        val artist =
            linkedArtist ?: sameName ?: artists.save(
                Artist(name = remote.name, fingerprint = FingerprintUtil.artistFp(remote.name)),
            )
        link(EntityType.ARTIST, artist.id, ExternalEntityType.ARTIST, remote.id)
        return MusicBrainzArtistCreateResult(artist.id, remote.id, created)
    }

    suspend fun searchAlbum(albumId: Long): List<MusicBrainzAlbumCandidateDto> {
        val album = requireAlbum(albumId)
        val artist = requireArtist(album.artistId)
        return client.searchReleaseGroups(album.title, artist.name).map { it.toDto() }
    }

    suspend fun previewAlbum(
        albumId: Long,
        releaseGroupMbid: String,
    ): MusicBrainzAlbumPreviewResponse {
        val album = requireAlbum(albumId)
        val candidate = client.getReleaseGroup(releaseGroupMbid)
        val genres = candidate.genres.mapNotNull { it.name }.normalized()
        val tags =
            candidate.tags
                .mapNotNull { it.name }
                .normalized()
                .filterNot { it in genres }
        val year = candidate.firstReleaseDate?.take(4)?.toIntOrNull()
        val changes =
            buildList {
                add("Vincular release group ${candidate.id}")
                candidate.artistCredit
                    .firstOrNull()
                    ?.artist
                    ?.let { add("Vincular artista ${it.id}") }
                if (album.year == null && year != null) add("Preencher ano com $year")
                if (genres.isNotEmpty()) add("Adicionar ${genres.size} gêneros")
                if (tags.isNotEmpty()) add("Adicionar ${tags.size} tags")
            }
        return MusicBrainzAlbumPreviewResponse(candidate.toDto(), genres, tags, changes)
    }

    suspend fun applyAlbum(
        albumId: Long,
        releaseGroupMbid: String,
    ): MusicBrainzEnrichmentResult {
        val preview = previewAlbum(albumId, releaseGroupMbid)
        val album = requireAlbum(albumId)
        link(EntityType.ALBUM, album.id, ExternalEntityType.RELEASE_GROUP, preview.candidate.releaseGroupMbid)
        preview.candidate.artistMbid?.let { link(EntityType.ARTIST, album.artistId, ExternalEntityType.ARTIST, it) }
        val yearAdded =
            album.year == null &&
                preview.candidate.firstReleaseYear != null &&
                albums.promoteNullYear(album.id, preview.candidate.firstReleaseYear) > 0
        albumTermsService.addMusicBrainzTerms(album, preview.genres, preview.tags)
        reconcileLegacyRelease(album.id)
        return MusicBrainzEnrichmentResult(
            albumId = album.id,
            artistId = album.artistId,
            releaseGroupMbid = preview.candidate.releaseGroupMbid,
            artistMbid = preview.candidate.artistMbid,
            yearAdded = yearAdded,
            genresAdded = preview.genres.size,
            tagsAdded = preview.tags.size,
        )
    }

    suspend fun searchArtist(artistId: Long): List<MusicBrainzArtistCandidateDto> {
        val artist = requireArtist(artistId)
        return client.searchArtists(artist.name).map { it.toDto() }
    }

    fun applyArtist(
        artistId: Long,
        artistMbid: String,
    ): MusicBrainzEnrichmentResult {
        requireArtist(artistId)
        link(EntityType.ARTIST, artistId, ExternalEntityType.ARTIST, artistMbid)
        return MusicBrainzEnrichmentResult(artistId = artistId, artistMbid = artistMbid)
    }

    suspend fun getArtistDiscography(artistId: Long): MusicBrainzDiscographyPreviewResponse {
        requireArtist(artistId)
        val artistMbid = requireArtistMbid(artistId)
        val localAlbums = albums.findAllByArtistId(artistId)
        val items = client.getArtistReleaseGroups(artistMbid).map { classifyDiscographyItem(it, localAlbums) }
        return MusicBrainzDiscographyPreviewResponse(
            artistId = artistId,
            items = items.sortedWith(compareBy({ it.firstReleaseYear ?: Int.MAX_VALUE }, { it.title.lowercase() })),
            creatableCount = items.count { it.status == MusicBrainzDiscographyStatus.MISSING },
        )
    }

    suspend fun importArtistDiscography(
        artistId: Long,
        requestedMbids: List<String>,
    ): MusicBrainzDiscographyImportResult {
        val artist = requireArtist(artistId)
        val requested = requestedMbids.map(String::trim).filter(String::isNotBlank).distinct()
        if (requested.isEmpty()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at least one release group")
        if (requested.size > 50) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Select at most 50 release groups")
        val artistMbid = requireArtistMbid(artistId)
        val remoteById = client.getArtistReleaseGroups(artistMbid).associateBy { it.id }
        if (requested.any { it !in remoteById }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Release group does not belong to this artist")
        }

        val createdIds = mutableListOf<Long>()
        val skipped = mutableListOf<String>()
        requested.forEach { mbid ->
            val candidate = remoteById.getValue(mbid)
            val classified = classifyDiscographyItem(candidate, albums.findAllByArtistId(artistId))
            if (classified.status != MusicBrainzDiscographyStatus.MISSING) {
                skipped += mbid
                return@forEach
            }
            val year = candidate.firstReleaseDate?.take(4)?.toIntOrNull()
            val titleKey = TitleKeyUtil.albumTitleKey(candidate.title).ifBlank { "unknown" }
            val details = client.getReleaseGroup(mbid)
            val genres = details.genres.mapNotNull { it.name }.normalized()
            val tags =
                details.tags
                    .mapNotNull { it.name }
                    .normalized()
                    .filterNot { it in genres }
            val album =
                albums.save(
                    Album(
                        artistId = artist.id,
                        title = candidate.title,
                        titleKey = titleKey,
                        year = year,
                        fingerprint = FingerprintUtil.albumFp(titleKey, artist.id),
                    ),
                )
            link(EntityType.ALBUM, album.id, ExternalEntityType.RELEASE_GROUP, mbid)
            albumTermsService.addMusicBrainzTerms(album, genres, tags)
            createdIds += album.id
        }
        return MusicBrainzDiscographyImportResult(artistId, createdIds, skipped)
    }

    private fun classifyDiscographyItem(
        candidate: dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupCandidate,
        localAlbums: List<Album>,
    ): MusicBrainzDiscographyItemDto {
        val linked = externalIds.findByProviderAndExternalId(Provider.MUSICBRAINZ, candidate.id)
        val linkedAlbum = linked?.takeIf { it.entityType == EntityType.ALBUM }?.entityId
        val titleKey = TitleKeyUtil.albumTitleKey(candidate.title)
        val year = candidate.firstReleaseDate?.take(4)?.toIntOrNull()
        val possible = localAlbums.firstOrNull { it.titleKey == titleKey }
        val status =
            when {
                linkedAlbum != null -> MusicBrainzDiscographyStatus.LINKED
                possible != null -> MusicBrainzDiscographyStatus.POSSIBLE_MATCH
                else -> MusicBrainzDiscographyStatus.MISSING
            }
        return MusicBrainzDiscographyItemDto(
            releaseGroupMbid = candidate.id,
            title = candidate.title,
            firstReleaseYear = year,
            primaryType = candidate.primaryType,
            disambiguation = candidate.disambiguation,
            status = status,
            localAlbumId = linkedAlbum ?: possible?.id,
        )
    }

    private fun requireArtistMbid(artistId: Long): String =
        externalIds
            .findByEntityTypeAndEntityIdAndProviderAndExternalEntityType(
                EntityType.ARTIST,
                artistId,
                Provider.MUSICBRAINZ,
                ExternalEntityType.ARTIST,
            )?.externalId
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Artist must be linked to MusicBrainz first")

    private suspend fun reconcileLegacyRelease(albumId: Long) {
        val legacy =
            externalIds.findByEntityTypeAndEntityIdAndProviderAndExternalEntityTypeIsNull(
                EntityType.ALBUM,
                albumId,
                Provider.MUSICBRAINZ,
            ) ?: return
        try {
            val releaseGroupId = client.resolveReleaseGroupFromRelease(legacy.externalId) ?: return
            externalIds.save(legacy.copy(externalEntityType = ExternalEntityType.RELEASE))
            val releaseGroup =
                externalIds.findByEntityTypeAndEntityIdAndProviderAndExternalEntityType(
                    EntityType.ALBUM,
                    albumId,
                    Provider.MUSICBRAINZ,
                    ExternalEntityType.RELEASE_GROUP,
                )
            if (releaseGroup == null) link(EntityType.ALBUM, albumId, ExternalEntityType.RELEASE_GROUP, releaseGroupId)
        } catch (_: Exception) {
            // Preserve unresolved legacy identifiers for explicit review.
        }
    }

    private fun link(
        entityType: EntityType,
        entityId: Long,
        externalType: ExternalEntityType,
        mbid: String,
    ) {
        val current =
            externalIds.findByEntityTypeAndEntityIdAndProviderAndExternalEntityType(
                entityType,
                entityId,
                Provider.MUSICBRAINZ,
                externalType,
            )
        if (current?.externalId == mbid) return
        try {
            val legacyWithSameMbid = externalIds.findByProviderAndExternalId(Provider.MUSICBRAINZ, mbid)
            if (current == null && legacyWithSameMbid?.entityType == entityType && legacyWithSameMbid.entityId == entityId) {
                externalIds.save(legacyWithSameMbid.copy(externalEntityType = externalType))
                return
            }
            externalIds.save(
                current?.copy(externalId = mbid)
                    ?: ExternalIdentifier(
                        entityType = entityType,
                        entityId = entityId,
                        provider = Provider.MUSICBRAINZ,
                        externalEntityType = externalType,
                        externalId = mbid,
                    ),
            )
        } catch (e: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "MusicBrainz identifier is linked to another local entity", e)
        }
    }

    private fun requireAlbum(id: Long) =
        albums.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found") }

    private fun requireArtist(id: Long) =
        artists.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Artist not found") }

    private fun dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupCandidate.toDto(): MusicBrainzAlbumCandidateDto {
        val credit = artistCredit.firstOrNull()
        return MusicBrainzAlbumCandidateDto(
            releaseGroupMbid = id,
            title = title,
            artistName = credit?.name ?: credit?.artist?.name ?: "Unknown artist",
            artistMbid = credit?.artist?.id,
            firstReleaseYear = firstReleaseDate?.take(4)?.toIntOrNull(),
            primaryType = primaryType,
            disambiguation = disambiguation,
        )
    }

    private fun Collection<String>.normalized() = map { it.trim().lowercase() }.filter { it.isNotBlank() }.distinct().take(20)

    private fun dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistCandidate.toDto() =
        MusicBrainzArtistCandidateDto(id, name, disambiguation, country)
}
