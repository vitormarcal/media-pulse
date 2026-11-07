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

enum class GameImageExistence { IN_GALLERY, IN_LIBRARY, NOT_FOUND, UNAVAILABLE }

data class GameImageLookupResult(
    val id: String,
    val state: GameImageExistence,
)

data class GameImageLookupResponse(
    val available: Boolean,
    val results: List<GameImageLookupResult>,
)
