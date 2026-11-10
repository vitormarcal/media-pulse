package dev.marcal.mediapulse.server.api.image

data class TmdbImageCandidateDto(
    val id: String,
    val kind: String,
    val url: String,
    val previewUrl: String,
    val width: Int?,
    val height: Int?,
    val provider: String = "TMDB",
)

data class TmdbImageCandidatesResponse(
    val images: List<TmdbImageCandidateDto>,
    val warnings: List<String> = emptyList(),
)

data class TmdbImageSelectionRequest(
    val ids: List<String?> = emptyList(),
)

data class TmdbImageSelectionResponse(
    val savedIds: List<String>,
    val failedIds: List<String>,
)

data class TmdbImageLookupResult(
    val id: String,
    val state: String,
)

data class TmdbImageLookupResponse(
    val available: Boolean,
    val results: List<TmdbImageLookupResult>,
)
