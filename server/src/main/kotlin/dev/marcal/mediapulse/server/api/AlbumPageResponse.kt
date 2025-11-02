package dev.marcal.mediapulse.server.api

import java.time.Instant
import java.time.LocalDate

data class AlbumPageResponse(
    val albumId: Long,
    val albumTitle: String,
    val artistId: Long,
    val artistName: String,
    val year: Int?,
    val coverUrl: String?,
    val lastPlayed: Instant?,
    val totalPlays: Long,
    val tracks: List<AlbumTrackRow>,
    val playsByDay: List<PlaysByDayRow>,
)

data class AlbumTrackRow(
    val trackId: Long,
    val title: String,
    val discNumber: Int?,
    val trackNumber: Int?,
    val playCount: Long,
    val lastPlayed: Instant?,
)

data class PlaysByDayRow(
    val day: LocalDate,
    val plays: Long,
)
