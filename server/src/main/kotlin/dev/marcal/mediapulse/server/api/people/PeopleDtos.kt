package dev.marcal.mediapulse.server.api.people

enum class PersonHistoryCategory {
    CAST,
    DIRECTING,
    WRITING,
}

data class PersonHistoryItemDto(
    val personId: Long,
    val name: String,
    val slug: String,
    val profileUrl: String?,
    val watchedWorksCount: Long,
)

data class PersonHistoryPageResponse(
    val items: List<PersonHistoryItemDto>,
    val nextOffset: Int?,
)
