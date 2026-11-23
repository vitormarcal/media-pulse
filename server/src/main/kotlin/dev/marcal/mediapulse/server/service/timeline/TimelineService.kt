package dev.marcal.mediapulse.server.service.timeline

import dev.marcal.mediapulse.server.api.timeline.TimelineDomain
import dev.marcal.mediapulse.server.api.timeline.TimelineKind
import dev.marcal.mediapulse.server.api.timeline.TimelinePage
import dev.marcal.mediapulse.server.api.timeline.TimelinePeriods
import dev.marcal.mediapulse.server.repository.query.TimelineQueryRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.time.Instant
import java.time.YearMonth
import java.time.ZoneId
import java.util.Base64

@Service
class TimelineService(
    private val repository: TimelineQueryRepository,
) {
    fun page(
        domain: TimelineDomain?,
        kind: TimelineKind?,
        month: String?,
        cursor: String?,
        limit: Int,
    ): TimelinePage {
        if (limit !in 1..100) badRequest("limit deve estar entre 1 e 100")
        val before =
            month?.let {
                parseMonth(it)
                    .plusMonths(1)
                    .atDay(1)
                    .atStartOfDay(ZONE)
                    .toInstant()
            }
        val position = cursor?.let { decodeCursor(it, domain, kind, month) }
        val rows = repository.events(domain, kind, before, position?.first, position?.second, limit + 1)
        val items = rows.take(limit)
        val next = if (rows.size > limit) items.last().let { encodeCursor(it.sortAt, it.event.id, domain, kind, month) } else null
        return TimelinePage(items.map { it.event }, next, ZONE.id)
    }

    fun periods(
        domain: TimelineDomain?,
        kind: TimelineKind?,
    ): TimelinePeriods = TimelinePeriods(repository.periods(domain, kind), ZONE.id)

    private fun parseMonth(value: String): YearMonth =
        try {
            require(Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(value))
            YearMonth.parse(value)
        } catch (_: Exception) {
            badRequest("month deve usar YYYY-MM")
        }

    private fun scope(
        domain: TimelineDomain?,
        kind: TimelineKind?,
        month: String?,
    ) = "${domain?.name.orEmpty()};${kind?.name.orEmpty()};${month.orEmpty()}"

    private fun encodeCursor(
        at: Instant,
        id: String,
        domain: TimelineDomain?,
        kind: TimelineKind?,
        month: String?,
    ): String = Base64.getUrlEncoder().withoutPadding().encodeToString("$at|$id|${scope(domain, kind, month)}".toByteArray(Charsets.UTF_8))

    private fun decodeCursor(
        cursor: String,
        domain: TimelineDomain?,
        kind: TimelineKind?,
        month: String?,
    ): Pair<Instant, String> =
        try {
            require(cursor.length <= 1024)
            val parts = String(Base64.getUrlDecoder().decode(cursor), Charsets.UTF_8).split('|')
            require(parts.size == 3 && parts[1].isNotBlank() && parts[2] == scope(domain, kind, month))
            Instant.parse(parts[0]) to parts[1]
        } catch (_: Exception) {
            badRequest("Cursor inválido para este recorte")
        }

    private fun badRequest(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    companion object {
        val ZONE: ZoneId = ZoneId.of("America/Sao_Paulo")
    }
}
