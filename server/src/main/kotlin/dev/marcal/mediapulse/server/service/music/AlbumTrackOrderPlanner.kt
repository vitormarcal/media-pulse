package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.repository.AlbumMergeRepository

object AlbumTrackOrderPlanner {
    data class Plan(
        val links: List<AlbumMergeRepository.TrackLink>,
        val positionedTrackCount: Int,
        val unpositionedTrackCount: Int,
        val conflictedTrackCount: Int,
    )

    fun plan(
        links: List<AlbumMergeRepository.TrackLink>,
        baseAlbumId: Long,
    ): Plan {
        val selectedLinks =
            links
                .groupBy { it.trackId }
                .values
                .map { candidates ->
                    candidates.minWith(
                        compareBy<AlbumMergeRepository.TrackLink>(
                            { if (it.albumId == baseAlbumId) 0 else 1 },
                            { if (it.hasPosition) 0 else 1 },
                            { it.albumId },
                            { it.trackId },
                        ),
                    )
                }.sortedWith(
                    compareBy<AlbumMergeRepository.TrackLink>(
                        { if (it.albumId == baseAlbumId) 0 else 1 },
                        { it.albumId },
                        { it.trackId },
                    ),
                )

        val occupiedPositions = mutableSetOf<Pair<Int, Int>>()
        var conflicts = 0
        val plannedLinks =
            selectedLinks.map { link ->
                val position = link.position
                when {
                    position == null -> link.withoutPosition()
                    occupiedPositions.add(position) -> link
                    else -> {
                        conflicts++
                        link.withoutPosition()
                    }
                }
            }
        val positioned = plannedLinks.count { it.hasPosition }
        return Plan(
            links = plannedLinks,
            positionedTrackCount = positioned,
            unpositionedTrackCount = plannedLinks.size - positioned,
            conflictedTrackCount = conflicts,
        )
    }

    private val AlbumMergeRepository.TrackLink.hasPosition: Boolean
        get() = discNumber != null && trackNumber != null

    private val AlbumMergeRepository.TrackLink.position: Pair<Int, Int>?
        get() = if (hasPosition) discNumber!! to trackNumber!! else null

    private fun AlbumMergeRepository.TrackLink.withoutPosition() = copy(discNumber = null, trackNumber = null)
}
