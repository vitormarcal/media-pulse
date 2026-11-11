package dev.marcal.mediapulse.server.api.music

import java.time.Instant

data class AlbumContextDto(
    val releaseGroupMbid: String,
    val primaryType: String? = null,
    val secondaryTypes: List<String> = emptyList(),
    val firstReleaseDate: String? = null,
    val links: Map<String, String> = emptyMap(),
    val introduction: AlbumIntroductionDto? = null,
    val fetchedAt: Instant,
)

data class AlbumIntroductionDto(
    val text: String,
    val language: String,
    val sourceUrl: String,
    val revisionId: Long? = null,
    val license: String,
    val licenseUrl: String,
)

data class AlbumContextPreviewDto(
    val token: String,
    val current: AlbumContextDto?,
    val proposed: AlbumContextDto,
    val expiresAt: Instant,
)

data class AlbumContextApplyRequest(
    val token: String,
)
