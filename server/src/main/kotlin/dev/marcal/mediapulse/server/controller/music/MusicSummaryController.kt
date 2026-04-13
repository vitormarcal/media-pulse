package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.AlbumLibraryPageResponse
import dev.marcal.mediapulse.server.api.music.AlbumPageResponse
import dev.marcal.mediapulse.server.api.music.ArtistLibraryPageResponse
import dev.marcal.mediapulse.server.api.music.ArtistPageResponse
import dev.marcal.mediapulse.server.api.music.MusicByYearResponse
import dev.marcal.mediapulse.server.api.music.MusicStatsResponse
import dev.marcal.mediapulse.server.api.music.MusicSummaryResponse
import dev.marcal.mediapulse.server.api.music.RecentAlbumsPageResponse
import dev.marcal.mediapulse.server.api.music.SearchResponse
import dev.marcal.mediapulse.server.api.music.TrackLibraryPageResponse
import dev.marcal.mediapulse.server.api.music.TrackPageResponse
import dev.marcal.mediapulse.server.repository.MusicQueryRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.min

@RestController
@RequestMapping("/api/music")
class MusicSummaryController(
    private val repository: MusicQueryRepository,
) {
    @GetMapping("/summary")
    fun summary(
        @RequestParam(required = false, defaultValue = "week") range: String,
        @RequestParam(required = false) start: Instant?,
        @RequestParam(required = false) end: Instant?,
    ): MusicSummaryResponse {
        val (s, e) = resolveRange(range, start, end)
        return repository.getSummary(s, e)
    }

    @GetMapping("/recent-albums")
    fun recentAlbums(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): RecentAlbumsPageResponse = repository.getRecentAlbums(limit, cursor)

    @GetMapping("/library/artists")
    fun artistLibrary(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): ArtistLibraryPageResponse = repository.getArtistLibrary(limit, cursor)

    @GetMapping("/library/albums")
    fun albumLibrary(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): AlbumLibraryPageResponse = repository.getAlbumLibrary(limit, cursor)

    @GetMapping("/library/tracks")
    fun trackLibrary(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(required = false) cursor: String?,
    ): TrackLibraryPageResponse = repository.getTrackLibrary(limit, cursor)

    @GetMapping("/stats")
    fun stats(): MusicStatsResponse = repository.getStats()

    @GetMapping("/year/{year}")
    fun byYear(
        @PathVariable year: Int,
        @RequestParam(defaultValue = "80") limitAlbums: Int,
        @RequestParam(defaultValue = "12") limitArtists: Int,
        @RequestParam(defaultValue = "12") limitTracks: Int,
    ): MusicByYearResponse {
        val validatedYear = validateYear(year)
        val resolvedLimitAlbums = normalizeLimit("limitAlbums", limitAlbums)
        val resolvedLimitArtists = normalizeLimit("limitArtists", limitArtists)
        val resolvedLimitTracks = normalizeLimit("limitTracks", limitTracks)
        val (start, end) = yearRange(validatedYear)
        return repository.getByYear(
            year = validatedYear,
            start = start,
            end = end,
            limitAlbums = resolvedLimitAlbums,
            limitArtists = resolvedLimitArtists,
            limitTracks = resolvedLimitTracks,
        )
    }

    @GetMapping("/tops/artists")
    fun topArtists(
        @RequestParam start: Instant,
        @RequestParam end: Instant,
        @RequestParam(defaultValue = "20") limit: Int,
    ) = repository.getTopArtists(start, end, limit)

    @GetMapping("/tops/albums")
    fun topAlbums(
        @RequestParam start: Instant,
        @RequestParam end: Instant,
        @RequestParam(defaultValue = "20") limit: Int,
    ) = repository.getTopAlbums(start, end, limit)

    @GetMapping("/tops/tracks")
    fun topTracks(
        @RequestParam start: Instant,
        @RequestParam end: Instant,
        @RequestParam(defaultValue = "20") limit: Int,
    ) = repository.getTopTracks(start, end, limit)

    @GetMapping("/tops/genres")
    fun topGenres(
        @RequestParam start: Instant,
        @RequestParam end: Instant,
        @RequestParam(defaultValue = "20") limit: Int,
    ) = repository.getTopGenres(start, end, limit)

    @GetMapping("/albums/{albumId}")
    fun albumPage(
        @PathVariable albumId: Long,
    ): AlbumPageResponse = repository.getAlbumPage(albumId)

    @GetMapping("/artists/{artistId}")
    fun artistPage(
        @PathVariable artistId: Long,
    ): ArtistPageResponse = repository.getArtistPage(artistId)

    @GetMapping("/tracks/{trackId}")
    fun trackPage(
        @PathVariable trackId: Long,
    ): TrackPageResponse = repository.getTrackPage(trackId)

    @GetMapping("/albums/never-played")
    fun neverPlayedAlbums(
        @RequestParam(defaultValue = "50") limit: Int,
    ) = repository.getNeverPlayedAlbums(limit)

    @GetMapping("/coverage/artists")
    fun artistCoverage(
        @RequestParam(defaultValue = "50") limit: Int,
    ) = repository.getArtistCoverage(limit)

    @GetMapping("/coverage/albums")
    fun albumCoverage(
        @RequestParam(defaultValue = "50") limit: Int,
    ) = repository.getAlbumCoverage(limit)

    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): SearchResponse = repository.search(q, limit)

    @GetMapping("/genres/trending")
    fun trendingGenres(
        @RequestParam start: Instant,
        @RequestParam end: Instant,
        @RequestParam compareStart: Instant,
        @RequestParam compareEnd: Instant,
        @RequestParam(defaultValue = "20") limit: Int,
    ) = repository.getTrendingGenres(start, end, compareStart, compareEnd, limit)

    @GetMapping("/genres/recent")
    fun recentGenres(
        @RequestParam(defaultValue = "50") limit: Int, // últimos N plays
    ) = repository.getRecentGenres(limit)

    @GetMapping("/genres/underplayed")
    fun underplayedGenres(
        @RequestParam start: Instant,
        @RequestParam end: Instant,
        @RequestParam(defaultValue = "3") minLibraryAlbums: Int,
        @RequestParam(defaultValue = "20") limit: Int,
    ) = repository.getUnderplayedGenres(start, end, minLibraryAlbums, limit)

    @GetMapping("/genres/top-by-source")
    fun topGenresBySource(
        @RequestParam start: Instant,
        @RequestParam end: Instant,
        @RequestParam(defaultValue = "10") limit: Int,
    ) = repository.getTopGenresBySource(start, end, limit)

    private fun resolveRange(
        range: String,
        start: Instant?,
        end: Instant?,
    ): Pair<Instant, Instant> =
        when (range.lowercase()) {
            "week" -> {
                val e = Instant.now()
                val s = e.minus(Duration.ofDays(7))
                s to e
            }
            "month" -> {
                val e = Instant.now()
                val s = e.minus(Duration.ofDays(30))
                s to e
            }
            "custom" -> requireNotNull(start) to requireNotNull(end)
            else -> throw IllegalArgumentException("range inválido")
        }

    private fun validateYear(year: Int): Int {
        val maxYear = LocalDate.now(ZoneOffset.UTC).year + 1
        if (year < 1900 || year > maxYear) {
            throw IllegalArgumentException("year inválido")
        }
        return year
    }

    private fun normalizeLimit(
        name: String,
        value: Int,
    ): Int {
        require(value >= 1) { "$name deve ser >= 1" }
        return min(value, 1000)
    }

    private fun yearRange(year: Int): Pair<Instant, Instant> {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val end = LocalDate.of(year, 12, 31).atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
        return start to end
    }
}
