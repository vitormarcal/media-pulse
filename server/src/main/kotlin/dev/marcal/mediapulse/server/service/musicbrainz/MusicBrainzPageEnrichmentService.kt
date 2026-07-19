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
import dev.marcal.mediapulse.server.model.music.Album
import dev.marcal.mediapulse.server.model.music.Artist
import dev.marcal.mediapulse.server.repository.crud.AlbumMusicBrainzReleaseIdRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
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
    private val albumReleaseIds: AlbumMusicBrainzReleaseIdRepository,
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
        val linkedArtist = artists.findByMusicbrainzArtistId(remote.id)
        val sameName = artists.findByFingerprint(FingerprintUtil.artistFp(remote.name))
        val created = linkedArtist == null && sameName == null
        val artist =
            linkedArtist ?: sameName ?: artists.save(
                Artist(name = remote.name, fingerprint = FingerprintUtil.artistFp(remote.name)),
            )
        linkArtist(artist.id, remote.id)
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
        linkReleaseGroup(album.id, preview.candidate.releaseGroupMbid)
        preview.candidate.artistMbid?.let { linkArtist(album.artistId, it) }
        val yearAdded =
            album.year == null &&
                preview.candidate.firstReleaseYear != null &&
                albums.promoteNullYear(album.id, preview.candidate.firstReleaseYear) > 0
        albumTermsService.addMusicBrainzTerms(album, preview.genres, preview.tags)
        reconcileReleaseGroup(album.id)
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
        linkArtist(artistId, artistMbid)
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
            linkReleaseGroup(album.id, mbid)
            albumTermsService.addMusicBrainzTerms(album, genres, tags)
            createdIds += album.id
        }
        return MusicBrainzDiscographyImportResult(artistId, createdIds, skipped)
    }

    private fun classifyDiscographyItem(
        candidate: dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupCandidate,
        localAlbums: List<Album>,
    ): MusicBrainzDiscographyItemDto {
        val linkedAlbum = albums.findByMusicbrainzReleaseGroupId(candidate.id)?.id
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
        requireArtist(artistId).musicbrainzArtistId
            ?: throw ResponseStatusException(HttpStatus.CONFLICT, "Artist must be linked to MusicBrainz first")

    private fun linkArtist(
        artistId: Long,
        mbid: String,
    ) {
        val artist = requireArtist(artistId)
        if (artist.musicbrainzArtistId == mbid) return
        if (artists.findByMusicbrainzArtistId(mbid)?.id?.let { it != artistId } == true) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "MusicBrainz identifier is linked to another local entity")
        }
        try {
            artists.save(artist.copy(musicbrainzArtistId = mbid, updatedAt = java.time.Instant.now()))
        } catch (e: DataIntegrityViolationException) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "MusicBrainz identifier is linked to another local entity", e)
        }
    }

    suspend fun reconcileReleaseGroup(albumId: Long) {
        val legacy = albumReleaseIds.findFirstByAlbumIdOrderByIdAsc(albumId) ?: return
        try {
            val releaseGroupId = client.resolveReleaseGroupFromRelease(legacy.releaseId) ?: return
            if (albums.findById(albumId).orElseThrow().musicbrainzReleaseGroupId == null) {
                linkReleaseGroup(albumId, releaseGroupId)
            }
        } catch (_: Exception) {
            // Preserve unresolved legacy identifiers for explicit review.
        }
    }

    private fun linkReleaseGroup(
        albumId: Long,
        mbid: String,
    ) {
        val album = requireAlbum(albumId)
        if (album.musicbrainzReleaseGroupId == mbid) return
        if (albums.findByMusicbrainzReleaseGroupId(mbid)?.id?.let { it != albumId } == true) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "MusicBrainz identifier is linked to another local entity")
        }
        try {
            albums.save(album.copy(musicbrainzReleaseGroupId = mbid, updatedAt = java.time.Instant.now()))
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
