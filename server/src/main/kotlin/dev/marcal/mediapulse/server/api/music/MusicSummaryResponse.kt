package dev.marcal.mediapulse.server.api.music

import java.time.Instant

data class MusicSummaryResponse(
    val range: RangeDto,
    val artistsCount: Long,
    val albumsCount: Long,
    val tracksCount: Long,
)

data class MusicByYearResponse(
    val year: Int,
    val range: RangeDto,
    val stats: MusicByYearStatsDto,
    val albums: List<AlbumLibraryRow>,
    val artists: List<TopArtistResponse>,
    val tracks: List<TopTrackResponse>,
)

data class MusicByYearStatsDto(
    val playsCount: Long,
    val uniqueArtistsCount: Long,
    val uniqueAlbumsCount: Long,
    val uniqueTracksCount: Long,
)

data class MusicStatsResponse(
    val total: MusicTotalStatsDto,
    val years: List<MusicYearStatsDto>,
    val latestPlayAt: Instant?,
    val firstPlayAt: Instant?,
)

data class MusicTotalStatsDto(
    val playsCount: Long,
    val uniqueArtistsCount: Long,
    val uniqueAlbumsCount: Long,
    val uniqueTracksCount: Long,
)

data class MusicYearStatsDto(
    val year: Int,
    val playsCount: Long,
    val uniqueArtistsCount: Long,
    val uniqueAlbumsCount: Long,
    val uniqueTracksCount: Long,
)

data class RangeDto(
    val start: Instant,
    val end: Instant,
)
