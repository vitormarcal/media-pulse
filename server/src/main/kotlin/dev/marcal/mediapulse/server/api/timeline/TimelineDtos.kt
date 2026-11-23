package dev.marcal.mediapulse.server.api.timeline

import java.time.Instant
import java.time.LocalDate

enum class TimelineDomain { MUSIC, BOOKS, MAGAZINES, MOVIES, SHOWS, GAMES, ARTWORKS, WORKOUTS }

enum class TimelineKind { ACTIVITY, ADDITION }

enum class TimelinePrecision { DAY, INSTANT }

data class TimelineLink(
    val title: String,
    val href: String,
    val imageUrl: String? = null,
    val subtitle: String? = null,
)

data class TimelineEvent(
    val id: String,
    val domain: TimelineDomain,
    val kind: TimelineKind,
    val date: LocalDate,
    val occurredAt: Instant?,
    val precision: TimelinePrecision,
    val action: String,
    val title: String,
    val subtitle: String?,
    val href: String,
    val imageUrl: String?,
    val relatedItems: List<TimelineLink> = emptyList(),
)

data class TimelinePage(
    val items: List<TimelineEvent>,
    val nextCursor: String?,
    val timeZone: String,
)

data class TimelineMonth(
    val month: String,
    val count: Long,
)

data class TimelinePeriods(
    val months: List<TimelineMonth>,
    val timeZone: String,
)
