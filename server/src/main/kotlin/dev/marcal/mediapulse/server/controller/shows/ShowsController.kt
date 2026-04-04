package dev.marcal.mediapulse.server.controller.shows

import dev.marcal.mediapulse.server.api.shows.CurrentlyWatchingShowDto
import dev.marcal.mediapulse.server.api.shows.ShowCardDto
import dev.marcal.mediapulse.server.api.shows.ShowDetailsResponse
import dev.marcal.mediapulse.server.api.shows.ShowsByYearResponse
import dev.marcal.mediapulse.server.api.shows.ShowsSearchResponse
import dev.marcal.mediapulse.server.api.shows.ShowsStatsResponse
import dev.marcal.mediapulse.server.api.shows.ShowsSummaryResponse
import dev.marcal.mediapulse.server.repository.TvShowQueryRepository
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.server.ResponseStatusException
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import kotlin.math.min

@RestController
@RequestMapping("/api/shows")
class ShowsController(
    private val repository: TvShowQueryRepository,
) {
    @GetMapping("/recent")
    fun recent(
        @RequestParam(defaultValue = "20") limit: Int,
    ): List<ShowCardDto> = repository.recent(limit)

    @GetMapping("/currently-watching")
    fun currentlyWatching(
        @RequestParam(defaultValue = "20") limit: Int,
        @RequestParam(defaultValue = "90") activeWithinDays: Int,
    ): List<CurrentlyWatchingShowDto> {
        val resolvedLimit = normalizeLimit("limit", limit)
        val resolvedActiveWithinDays = normalizePositive("activeWithinDays", activeWithinDays)
        val activeSince = Instant.now().minus(Duration.ofDays(resolvedActiveWithinDays.toLong()))
        return repository.currentlyWatching(resolvedLimit, activeSince)
    }

    @GetMapping("/{showId}")
    fun details(
        @PathVariable showId: Long,
    ): ShowDetailsResponse = repository.getShowDetails(showId)

    @GetMapping("/slug/{slug}")
    fun detailsBySlug(
        @PathVariable slug: String,
    ): ShowDetailsResponse = repository.getShowDetailsBySlug(slug)

    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): ShowsSearchResponse = repository.search(q, limit)

    @GetMapping("/summary")
    fun summary(
        @RequestParam(defaultValue = "month") range: String,
        @RequestParam(required = false) start: Instant?,
        @RequestParam(required = false) end: Instant?,
    ): ShowsSummaryResponse {
        val (resolvedStart, resolvedEnd) = resolveRange(range, start, end)
        return repository.summary(resolvedStart, resolvedEnd)
    }

    @GetMapping("/stats")
    fun stats(): ShowsStatsResponse = repository.stats()

    @GetMapping("/year/{year}")
    fun byYear(
        @PathVariable year: Int,
        @RequestParam(defaultValue = "200") limitWatched: Int,
        @RequestParam(defaultValue = "200") limitUnwatched: Int,
    ): ShowsByYearResponse {
        val validatedYear = validateYear(year)
        val resolvedLimitWatched = normalizeLimit("limitWatched", limitWatched)
        val resolvedLimitUnwatched = normalizeLimit("limitUnwatched", limitUnwatched)
        val (start, end) = yearRange(validatedYear)
        return repository.byYear(
            year = validatedYear,
            start = start,
            end = end,
            limitWatched = resolvedLimitWatched,
            limitUnwatched = resolvedLimitUnwatched,
        )
    }

    private fun resolveRange(
        range: String,
        start: Instant?,
        end: Instant?,
    ): Pair<Instant, Instant> =
        when (range.lowercase()) {
            "month" -> {
                val e = Instant.now()
                val s = e.minus(Duration.ofDays(30))
                s to e
            }
            "year" -> {
                val e = Instant.now()
                val s = e.minus(Duration.ofDays(365))
                s to e
            }
            "custom" -> {
                if (start == null || end == null) {
                    throw IllegalArgumentException("range inválido")
                }
                start to end
            }
            else -> throw IllegalArgumentException("range inválido")
        }

    private fun validateYear(year: Int): Int {
        val maxYear = LocalDate.now(ZoneOffset.UTC).year + 1
        if (year < 1900 || year > maxYear) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "year inválido")
        }
        return year
    }

    private fun normalizeLimit(
        name: String,
        value: Int,
    ): Int {
        if (value < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$name deve ser >= 1")
        }
        return min(value, 1000)
    }

    private fun normalizePositive(
        name: String,
        value: Int,
    ): Int {
        if (value < 1) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "$name deve ser >= 1")
        }
        return value
    }

    private fun yearRange(year: Int): Pair<Instant, Instant> {
        val start = LocalDate.of(year, 1, 1).atStartOfDay(ZoneOffset.UTC).toInstant()
        val end = LocalDate.of(year, 12, 31).atTime(23, 59, 59).toInstant(ZoneOffset.UTC)
        return start to end
    }
}
