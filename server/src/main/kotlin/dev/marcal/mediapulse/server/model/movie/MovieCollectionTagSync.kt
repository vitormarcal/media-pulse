package dev.marcal.mediapulse.server.model.movie

import java.util.UUID

data class MovieCollectionTagTarget(
    val assetId: UUID,
    val entityType: String,
    val entityId: Long,
)

data class MovieCollectionTagSnapshot(
    val movieId: Long,
    val revision: Long,
    val claimToken: UUID,
    val collectionNames: List<String>,
    val targets: List<MovieCollectionTagTarget>,
)
