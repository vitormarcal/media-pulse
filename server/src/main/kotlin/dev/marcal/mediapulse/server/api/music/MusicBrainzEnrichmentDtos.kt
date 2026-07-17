package dev.marcal.mediapulse.server.api.music

data class MusicBrainzLinkDto(
    val mbid: String,
    val entityType: String,
)

data class MusicBrainzAlbumCandidateDto(
    val releaseGroupMbid: String,
    val title: String,
    val artistName: String,
    val artistMbid: String?,
    val firstReleaseYear: Int?,
    val primaryType: String?,
    val disambiguation: String?,
)

data class MusicBrainzArtistCandidateDto(
    val artistMbid: String,
    val name: String,
    val disambiguation: String?,
    val country: String?,
)

data class MusicBrainzAlbumPreviewResponse(
    val candidate: MusicBrainzAlbumCandidateDto,
    val genres: List<String>,
    val tags: List<String>,
    val changes: List<String>,
    val preservedFields: List<String> = listOf("title", "artist", "coverUrl", "tracklist"),
)

data class MusicBrainzAlbumApplyRequest(
    val releaseGroupMbid: String,
)

data class MusicBrainzArtistApplyRequest(
    val artistMbid: String,
)

data class MusicBrainzArtistCreateRequest(
    val artistMbid: String,
)

data class MusicBrainzArtistCreateResult(
    val artistId: Long,
    val artistMbid: String,
    val created: Boolean,
)

enum class MusicBrainzDiscographyStatus {
    LINKED,
    POSSIBLE_MATCH,
    MISSING,
}

data class MusicBrainzDiscographyItemDto(
    val releaseGroupMbid: String,
    val title: String,
    val firstReleaseYear: Int?,
    val primaryType: String?,
    val disambiguation: String?,
    val status: MusicBrainzDiscographyStatus,
    val localAlbumId: Long? = null,
)

data class MusicBrainzDiscographyPreviewResponse(
    val artistId: Long,
    val items: List<MusicBrainzDiscographyItemDto>,
    val creatableCount: Int,
)

data class MusicBrainzDiscographyImportRequest(
    val releaseGroupMbids: List<String>,
)

data class MusicBrainzDiscographyImportResult(
    val artistId: Long,
    val createdAlbumIds: List<Long>,
    val skippedReleaseGroupMbids: List<String>,
)

data class MusicBrainzEnrichmentResult(
    val albumId: Long? = null,
    val artistId: Long,
    val releaseGroupMbid: String? = null,
    val artistMbid: String? = null,
    val yearAdded: Boolean = false,
    val genresAdded: Int = 0,
    val tagsAdded: Int = 0,
)
