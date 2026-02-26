package dev.marcal.mediapulse.server.controller.movies

import dev.marcal.mediapulse.server.api.movies.MovieCardDto
import dev.marcal.mediapulse.server.api.movies.MovieDetailsResponse
import dev.marcal.mediapulse.server.api.movies.MoviesSearchResponse
import dev.marcal.mediapulse.server.api.movies.MoviesSummaryResponse
import dev.marcal.mediapulse.server.repository.MovieQueryRepository
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import java.time.Duration
import java.time.Instant

@RestController
@RequestMapping("/api/movies")
class MoviesController(
    private val repository: MovieQueryRepository,
) {
    @GetMapping("/recent")
    fun recent(
        @RequestParam(defaultValue = "20") limit: Int,
    ): List<MovieCardDto> = repository.recent(limit)

    @GetMapping("/{movieId}")
    fun details(
        @PathVariable movieId: Long,
    ): MovieDetailsResponse = repository.getMovieDetails(movieId)

    @GetMapping("/search")
    fun search(
        @RequestParam q: String,
        @RequestParam(defaultValue = "10") limit: Int,
    ): MoviesSearchResponse = repository.search(q, limit)

    @GetMapping("/summary")
    fun summary(
        @RequestParam(defaultValue = "month") range: String,
        @RequestParam(required = false) start: Instant?,
        @RequestParam(required = false) end: Instant?,
    ): MoviesSummaryResponse {
        val (resolvedStart, resolvedEnd) = resolveRange(range, start, end)
        return repository.summary(resolvedStart, resolvedEnd)
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
}
