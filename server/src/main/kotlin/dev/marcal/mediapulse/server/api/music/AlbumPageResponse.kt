package dev.marcal.mediapulse.server.api.music

import dev.marcal.mediapulse.server.api.comments.MediaCommentDto
import dev.marcal.mediapulse.server.api.ratings.MediaRatingDto
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
    val rating: MediaRatingDto? = null,
    val tracks: List<AlbumTrackRow>,
    val playsByDay: List<PlaysByDayRow>,
    val terms: List<AlbumTermDto> = emptyList(),
    val comments: List<MediaCommentDto> = emptyList(),
    val musicBrainz: MusicBrainzLinkDto? = null,
)

data class AlbumTrackRow(
    val trackId: Long,
    val title: String,
    val discNumber: Int?,
    val trackNumber: Int?,
    val playCount: Long,
    val lastPlayed: Instant?,
    val rating: MediaRatingDto? = null,
)

data class PlaysByDayRow(
    val day: LocalDate,
    val plays: Long,
)
