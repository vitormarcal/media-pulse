package dev.marcal.mediapulse.server.api.music

import java.time.Instant

data class ArtistBiographyDto(
    val artistMbid: String,
    val introduction: WikipediaIntroductionDto,
    val fetchedAt: Instant,
)

data class ArtistBiographyPreviewDto(
    val token: String?,
    val current: ArtistBiographyDto?,
    val proposed: ArtistBiographyDto?,
    val expiresAt: Instant?,
)

data class ArtistBiographyApplyRequest(
    val token: String,
)
