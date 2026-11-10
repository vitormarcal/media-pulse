package dev.marcal.mediapulse.server.api.movies

data class MovieImageCandidateDto(
    val id: String,
    val kind: String,
    val url: String,
    val previewUrl: String,
    val width: Int?,
    val height: Int?,
    val provider: String = "TMDB",
)

data class MovieImageCandidatesResponse(
    val images: List<MovieImageCandidateDto>,
    val warnings: List<String> = emptyList(),
)

data class MovieImageSelectionRequest(
    val ids: List<String?> = emptyList(),
)

data class MovieImageSelectionResponse(
    val savedIds: List<String>,
    val failedIds: List<String>,
)

data class MovieImageLookupResult(
    val id: String,
    val state: String,
)

data class MovieImageLookupResponse(
    val available: Boolean,
    val results: List<MovieImageLookupResult>,
)
