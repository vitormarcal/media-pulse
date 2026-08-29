package dev.marcal.mediapulse.server.api.music

import java.time.Instant

data class DuplicateArtistReviewResponse(
    val items: List<DuplicateArtistSuggestionResponse>,
)

data class DuplicateArtistSuggestionResponse(
    val reason: String,
    val confidence: String,
    val suggestedTargetArtistId: Long,
    val candidates: List<ArtistMergeCandidateResponse>,
)

data class ArtistMergeCatalogResponse(
    val artists: List<ArtistMergeCandidateResponse>,
)

data class ArtistMergeCandidateResponse(
    val artistId: Long,
    val name: String,
    val profileImageUrl: String?,
    val spotifyId: String?,
    val musicBrainzArtistId: String?,
    val artistType: String?,
    val areaName: String?,
    val disambiguation: String?,
    val albumCount: Long,
    val trackCount: Long,
    val playbackCount: Long,
    val lastPlayed: Instant?,
    val rating: Int?,
    val aliases: List<String>,
)

data class ArtistMergePreviewRequest(
    val targetArtistId: Long,
    val sourceArtistIds: List<Long>,
)

data class ArtistMergePreviewResponse(
    val targetArtistId: Long,
    val candidates: List<ArtistMergeCandidateResponse>,
    val totalAlbums: Long,
    val totalTracks: Long,
    val totalPlaybacks: Long,
    val warnings: List<String>,
)

data class ArtistMergeRequest(
    val targetArtistId: Long,
    val sourceArtistIds: List<Long>,
    val nameFromArtistId: Long = targetArtistId,
    val imageFromArtistId: Long = targetArtistId,
    val musicBrainzFromArtistId: Long = targetArtistId,
    val ratingFromArtistId: Long? = targetArtistId,
    val preserveAliasArtistIds: List<Long> = emptyList(),
)

data class ArtistMergeResponse(
    val artistId: Long,
    val mergedArtistIds: List<Long>,
    val movedAlbums: Int,
    val movedTracks: Int,
    val movedComments: Int,
    val mergedGenres: Int,
    val storedNameAliases: Int,
)
