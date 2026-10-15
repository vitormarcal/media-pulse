package dev.marcal.mediapulse.server.api.music

import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import java.time.Instant
import java.util.UUID

data class AlbumCreditBackfillPreviewRequest(
    val albumIds: List<Long> = emptyList(),
    val limit: Int = 5,
)

enum class AlbumCreditBackfillStatus { PENDING, READY, SKIPPED, ERROR, APPLIED, CONFLICT, UNDONE, UNDO_CONFLICT }

data class AlbumCreditBackfillArtist(
    val externalId: String,
    val name: String,
    val creditedName: String,
    val joinPhrase: String = "",
)

data class AlbumCreditBackfillItem(
    val albumId: Long,
    val albumTitle: String,
    val status: AlbumCreditBackfillStatus,
    val reason: String? = null,
    val before: List<AlbumArtistDto> = emptyList(),
    val hadStoredCredits: Boolean = false,
    val beforeRevision: Long = 0,
    val provider: AlbumArtistSource? = null,
    val providerId: String? = null,
    val proposed: List<AlbumCreditBackfillArtist> = emptyList(),
    val after: List<AlbumArtistDto>? = null,
    val afterRevision: Long? = null,
)

data class AlbumCreditBackfillBatch(
    val batchId: UUID,
    val createdAt: Instant,
    val items: List<AlbumCreditBackfillItem>,
)
