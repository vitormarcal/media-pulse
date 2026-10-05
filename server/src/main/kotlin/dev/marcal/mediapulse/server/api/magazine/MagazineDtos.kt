package dev.marcal.mediapulse.server.api.magazine

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dev.marcal.mediapulse.server.api.comments.MediaCommentDto
import dev.marcal.mediapulse.server.model.magazine.MagazineReadStatus
import java.time.LocalDate

data class MagazinePublicationRequest(
    val name: String,
    val issn: String? = null,
)

data class MagazineIssueRequest(
    val publicationId: Long? = null,
    val publication: MagazinePublicationRequest? = null,
    val number: String? = null,
    val coverDate: String? = null,
    @JsonDeserialize(using = MagazineIntegerDeserializer::class)
    val totalPages: Int? = null,
    val read: MagazineReadRequest? = null,
    val removeCover: Boolean = false,
)

data class MagazineReadRequest(
    val status: MagazineReadStatus,
    val startedAt: LocalDate? = null,
    val finishedAt: LocalDate? = null,
    val progressPct: Double? = null,
    @JsonDeserialize(using = MagazineIntegerDeserializer::class)
    val currentPage: Int? = null,
)

data class MagazineIssueDto(
    val id: Long,
    val publication: MagazinePublicationDto,
    val number: String?,
    val coverDate: String?,
    val totalPages: Int?,
    val coverUrl: String?,
    val activityDate: LocalDate,
    val latestRead: MagazineReadDto?,
)

data class MagazineDetailsDto(
    val issue: MagazineIssueDto,
    val reads: List<MagazineReadDto>,
    val comments: List<MediaCommentDto>,
)

data class MagazineLibraryDto(
    val items: List<MagazineIssueDto>,
    val nextPage: Int?,
)

data class MagazineOverviewDto(
    val inProgress: List<MagazineIssueDto>,
    val recent: List<MagazineIssueDto>,
    val numbersCount: Long,
    val completedReadsCount: Long,
)

data class MagazinePublicationDto(
    val id: Long,
    val name: String,
    val issn: String?,
)

data class MagazineReadDto(
    val id: Long,
    val issueId: Long,
    val status: MagazineReadStatus,
    val startedAt: LocalDate?,
    val finishedAt: LocalDate?,
    val progressPct: Double,
    val currentPage: Int?,
    val createdAt: java.time.Instant,
)
