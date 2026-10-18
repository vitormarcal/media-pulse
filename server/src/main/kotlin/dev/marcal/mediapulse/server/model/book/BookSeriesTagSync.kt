package dev.marcal.mediapulse.server.model.book

import java.util.UUID

data class BookSeriesTagTarget(
    val assetId: UUID,
    val entityType: String,
    val entityId: Long,
)

data class BookSeriesTagSnapshot(
    val bookId: Long,
    val revision: Long,
    val claimToken: UUID,
    val seriesNames: List<String>,
    val targets: List<BookSeriesTagTarget>,
)
