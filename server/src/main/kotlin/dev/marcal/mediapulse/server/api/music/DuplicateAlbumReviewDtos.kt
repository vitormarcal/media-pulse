package dev.marcal.mediapulse.server.api.music

import java.time.Instant

data class DuplicateAlbumReviewResponse(
    val items: List<DuplicateAlbumSuggestionResponse>,
)

data class AlbumMergeCatalogResponse(
    val artists: List<AlbumMergeArtistResponse>,
)

data class AlbumMergeArtistResponse(
    val artistId: Long,
    val artistName: String,
    val albums: List<AlbumMergeCandidateResponse>,
)

data class DuplicateAlbumSuggestionResponse(
    val artistId: Long,
    val artistName: String,
    val reason: String,
    val confidence: String,
    val suggestedTargetAlbumId: Long,
    val candidates: List<AlbumMergeCandidateResponse>,
)

data class AlbumMergeCandidateResponse(
    val albumId: Long,
    val title: String,
    val year: Int?,
    val coverUrl: String?,
    val trackCount: Long,
    val playbackCount: Long,
    val lastPlayed: Instant?,
    val spotifyIds: List<String>,
    val musicBrainzReleaseIds: List<String>,
    val musicBrainzReleaseGroupId: String?,
    val rating: Int?,
)

data class AlbumMergePreviewRequest(
    val targetAlbumId: Long,
    val sourceAlbumIds: List<Long>,
)

data class AlbumMergePreviewResponse(
    val artistId: Long,
    val artistName: String,
    val targetAlbumId: Long,
    val candidates: List<AlbumMergeCandidateResponse>,
    val totalTracks: Long,
    val totalPlaybacks: Long,
    val warnings: List<String>,
)

data class AlbumMergeRequest(
    val targetAlbumId: Long,
    val sourceAlbumIds: List<Long>,
    val titleFromAlbumId: Long = targetAlbumId,
    val coverFromAlbumId: Long = targetAlbumId,
    val yearFromAlbumId: Long = targetAlbumId,
    val ratingFromAlbumId: Long? = targetAlbumId,
)

data class AlbumMergeResponse(
    val albumId: Long,
    val mergedAlbumIds: List<Long>,
    val movedPlaybacks: Int,
    val migratedTrackLinks: Int,
    val linkedExternalIdentifiers: Int,
    val storedTitleAliases: Int,
)
