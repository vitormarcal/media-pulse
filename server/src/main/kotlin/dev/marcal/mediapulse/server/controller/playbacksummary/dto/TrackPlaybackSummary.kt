package dev.marcal.mediapulse.server.controller.playbacksummary.dto

data class TrackPlaybackSummary(
    val id: Long,
    val title: String,
    val album: String,
    val artist: String,
    val year: Int,
    val playbackCount: Long,
)
