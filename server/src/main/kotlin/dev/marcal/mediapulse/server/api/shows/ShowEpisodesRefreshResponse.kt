package dev.marcal.mediapulse.server.api.shows

data class ShowEpisodesRefreshResponse(
    val showId: Long,
    val addedSeasonsCount: Int,
    val addedEpisodesCount: Int,
)
