package dev.marcal.mediapulse.server.model.book

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.math.BigDecimal
import java.time.Instant

@Entity
@Table(
    name = "book_reads",
    uniqueConstraints = [UniqueConstraint(columnNames = ["source", "source_event_id"])],
)
data class BookRead(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(name = "book_id", nullable = false)
    val bookId: Long,
    @Column(name = "edition_id")
    val editionId: Long? = null,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val source: BookReadSource,
    @Column(name = "source_event_id", nullable = false)
    val sourceEventId: Long,
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    val status: BookReadStatus,
    @Column(name = "started_at")
    val startedAt: Instant? = null,
    @Column(name = "finished_at")
    val finishedAt: Instant? = null,
    @Column(name = "progress_pct")
    val progressPct: BigDecimal? = null,
    @Column(name = "progress_pages")
    val progressPages: Int? = null,
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
)
