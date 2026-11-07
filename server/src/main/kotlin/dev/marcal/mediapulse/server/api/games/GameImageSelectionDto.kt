package dev.marcal.mediapulse.server.api.games

data class GameImageCandidateDto(
    val id: String,
    val provider: String,
    val kind: String,
    val url: String,
    val previewUrl: String,
    val width: Int? = null,
    val height: Int? = null,
)

data class GameImageCandidatesResponse(
    val images: List<GameImageCandidateDto>,
    val warnings: List<String> = emptyList(),
)

data class GameImageSelectionRequest(
    val ids: List<String>,
)

data class GameImageSelectionResponse(
    val savedIds: List<String>,
    val failedIds: List<String>,
)
