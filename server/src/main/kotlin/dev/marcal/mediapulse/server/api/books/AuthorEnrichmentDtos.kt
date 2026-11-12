package dev.marcal.mediapulse.server.api.books

import java.time.Instant

enum class AuthorEnrichmentField { PHOTO, BIOGRAPHY, DATES, LOCATION, LINKS }

data class AuthorEnrichmentDto(
    val hardcoverId: Long,
    val externalName: String,
    val sourceUrl: String,
    val fetchedAt: Instant,
    val biography: String? = null,
    val born: String? = null,
    val died: String? = null,
    val location: String? = null,
    val links: List<String> = emptyList(),
    val photoUrl: String? = null,
    val photoSourceUrl: String? = null,
)

data class AuthorEnrichmentPreview(
    val token: String,
    val current: AuthorEnrichmentDto?,
    val proposed: AuthorEnrichmentDto,
    val expiresAt: Instant,
)

data class AuthorEnrichmentLookup(
    val reference: String,
)

data class AuthorEnrichmentApply(
    val token: String,
    val fields: Set<AuthorEnrichmentField>,
    val confirmIdentityChange: Boolean = false,
)
