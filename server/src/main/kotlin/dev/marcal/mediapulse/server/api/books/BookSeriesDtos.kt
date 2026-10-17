package dev.marcal.mediapulse.server.api.books

data class BookGroupDto(
    val id: Long,
    val name: String,
    val itemCount: Int,
)

data class BookGroupMemberDto(
    val id: Long,
    val name: String,
    val position: Int,
    val slug: String? = null,
    val coverUrl: String? = null,
)

data class BookGroupDetailsDto(
    val id: Long,
    val name: String,
    val items: List<BookGroupMemberDto>,
    val universes: List<BookGroupDto> = emptyList(),
)

data class BookGroupNameRequest(
    val name: String,
)

data class BookGroupOrderRequest(
    val ids: List<Long>,
)

data class BookSeriesSuggestionDto(
    val hardcoverId: Long,
    val name: String,
    val books: List<BookGroupMemberDto>,
)

data class ApplyBookSeriesSuggestionRequest(
    val hardcoverId: Long,
    val seriesId: Long? = null,
    val name: String,
    val bookIds: List<Long>,
)
