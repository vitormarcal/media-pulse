package dev.marcal.mediapulse.server.model.magazine

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.Instant
import java.time.LocalDate

enum class MagazineReadStatus { WANT_TO_READ, CURRENTLY_READING, READ, DID_NOT_FINISH }

@Entity
@Table(name = "magazine_publications")
data class MagazinePublication(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    val name: String,
    @Column(name = "normalized_name") val normalizedName: String,
    val issn: String? = null,
)

@Entity
@Table(name = "magazine_issues")
data class MagazineIssue(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "publication_id") val publicationId: Long,
    val number: String? = null,
    @Column(name = "cover_date") val coverDate: String? = null,
    @Column(name = "identity_key") val identityKey: String,
    @Column(name = "total_pages") val totalPages: Int? = null,
    @Column(name = "cover_url") val coverUrl: String? = null,
    @Column(name = "activity_date") val activityDate: LocalDate = LocalDate.now(),
)

@Entity
@Table(name = "magazine_reads")
data class MagazineRead(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "issue_id") val issueId: Long,
    @Enumerated(EnumType.STRING) val status: MagazineReadStatus,
    @Column(name = "started_at") val startedAt: LocalDate? = null,
    @Column(name = "finished_at") val finishedAt: LocalDate? = null,
    @Column(name = "progress_pct") val progressPct: Double = 0.0,
    @Column(name = "current_page") val currentPage: Int? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
)
