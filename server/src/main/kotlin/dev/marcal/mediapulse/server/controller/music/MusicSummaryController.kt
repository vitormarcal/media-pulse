package dev.marcal.mediapulse.server.controller.music

import dev.marcal.mediapulse.server.api.music.AlbumPageResponse
import dev.marcal.mediapulse.server.api.music.MusicSummaryResponse
import dev.marcal.mediapulse.server.api.music.RecentAlbumResponse
import dev.marcal.mediapulse.server.api.music.SearchResponse
import dev.marcal.mediapulse.server.repository.MusicQueryRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

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
    ): List<RecentAlbumResponse> = repository.getRecentAlbums(limit)

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

    @GetMapping("/albums/{albumId}")
    fun albumPage(
        @PathVariable albumId: Long,
    ): AlbumPageResponse = repository.getAlbumPage(albumId)

    @GetMapping("/albums/never-played")
    fun neverPlayedAlbums(
        @RequestParam(defaultValue = "50") limit: Int,
    ) = repository.getNeverPlayedAlbums(limit)

    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): SearchResponse = repository.search(q, limit)

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
}
