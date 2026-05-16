package dev.marcal.mediapulse.server.api.games

import dev.marcal.mediapulse.server.api.comments.MediaCommentDto
import dev.marcal.mediapulse.server.api.ratings.MediaRatingDto
import java.time.Instant

data class GameLibraryCardDto(
    val gameId: Long,
    val title: String,
    val originalTitle: String,
    val slug: String?,
    val year: Int?,
    val coverUrl: String?,
    val sessionCount: Long,
    val latestSessionAt: Instant?,
    val currentStatus: GameSessionStatusDto?,
)

data class GamesLibraryResponse(
    val items: List<GameLibraryCardDto>,
    val nextCursor: String?,
)

data class GamesStatsResponse(
    val totalGamesCount: Long,
    val sessionsCount: Long,
    val backlogCount: Long,
    val activeCount: Long,
    val completedCount: Long,
    val abandonedCount: Long,
    val latestSessionAt: Instant?,
)

data class GamesSearchResponse(
    val games: List<GameLibraryCardDto>,
)

data class GameImageDto(
    val id: Long,
    val url: String,
    val kind: String,
    val isPrimary: Boolean,
)

data class GameExternalIdDto(
    val provider: String,
    val externalId: String,
)

enum class GameSessionStatusDto {
    PLAYING,
    BACKLOG,
    COMPLETED,
    ABANDONED,
}

data class GameSessionDto(
    val sessionId: Long,
    val status: GameSessionStatusDto,
    val startedAt: Instant,
    val endedAt: Instant?,
    val notes: String?,
)

data class GameDetailsResponse(
    val gameId: Long,
    val title: String,
    val originalTitle: String,
    val slug: String?,
    val year: Int?,
    val description: String?,
    val coverUrl: String?,
    val images: List<GameImageDto>,
    val sessions: List<GameSessionDto>,
    val externalIds: List<GameExternalIdDto>,
    val rating: MediaRatingDto?,
    val comments: List<MediaCommentDto>,
)

data class ManualGameCatalogCreateRequest(
    val title: String,
    val year: Int? = null,
    val igdbId: String? = null,
)

data class ManualGameExternalIdView(
    val provider: String,
    val externalId: String,
)

data class ManualGameCatalogCreateResponse(
    val gameId: Long,
    val slug: String?,
    val title: String,
    val year: Int?,
    val coverUrl: String?,
    val createdGame: Boolean,
    val coverAssigned: Boolean,
    val externalIds: List<ManualGameExternalIdView>,
)

data class GameCatalogSuggestionDto(
    val igdbId: String,
    val title: String,
    val year: Int?,
    val overview: String?,
    val coverUrl: String?,
)

data class GameCatalogSuggestionsResponse(
    val query: String,
    val suggestions: List<GameCatalogSuggestionDto>,
)

data class GameSessionCreateRequest(
    val status: GameSessionStatusDto,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val notes: String? = null,
)

data class GameSessionCreateResponse(
    val session: GameSessionDto,
)

data class GameSessionUpdateRequest(
    val status: GameSessionStatusDto,
    val startedAt: Instant,
    val endedAt: Instant? = null,
    val notes: String? = null,
)
