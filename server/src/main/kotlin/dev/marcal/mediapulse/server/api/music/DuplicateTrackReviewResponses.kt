package dev.marcal.mediapulse.server.api.music

import java.time.Instant

data class DuplicateTrackReviewPageResponse(
    val items: List<DuplicateTrackGroupResponse>,
    val nextCursor: String?,
)

data class DuplicateTrackGroupResponse(
    val albumId: Long,
    val albumTitle: String,
    val albumYear: Int?,
    val albumCoverUrl: String?,
    val artistId: Long,
    val artistName: String,
    val groupKey: String,
    val normalizedTitle: String,
    val ignored: Boolean,
    val confidence: String,
    val suggestionReason: String,
    val suggestedTargetTrackId: Long,
    val candidates: List<DuplicateTrackCandidateResponse>,
)

data class DuplicateTrackCandidateResponse(
    val trackId: Long,
    val title: String,
    val durationMs: Int?,
    val discNumber: Int?,
    val trackNumber: Int?,
    val playbackCount: Long,
    val lastPlayed: Instant?,
    val hasMusicBrainz: Boolean,
    val hasSpotify: Boolean,
    val externalIdentifiers: List<String>,
)

data class DuplicateTrackIgnoreRequest(
    val albumId: Long,
    val groupKey: String,
    val ignored: Boolean = true,
)

data class DuplicateTrackMergeRequest(
    val albumId: Long,
    val groupKey: String,
    val targetTrackId: Long,
    val sourceTrackIds: List<Long>,
)

data class DuplicateTrackBatchMergeRequest(
    val merges: List<DuplicateTrackMergeRequest>,
)

data class DuplicateTrackMergeResponse(
    val albumId: Long,
    val groupKey: String,
    val targetTrackId: Long,
    val mergedTrackIds: List<Long>,
    val deletedDuplicatePlaybacks: Int,
    val movedPlaybacks: Int,
    val linkedExternalIdentifiers: Int,
    val migratedAlbumLinks: Int,
)

data class DuplicateTrackBatchMergeResponse(
    val processedGroups: Int,
    val results: List<DuplicateTrackMergeResponse>,
)
