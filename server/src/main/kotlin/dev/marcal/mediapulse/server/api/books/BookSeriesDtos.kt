package dev.marcal.mediapulse.server.api.books

data class BookGroupDto(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val description: String? = null,
    val authors: List<AuthorDto> = emptyList(),
    val coverUrls: List<String> = emptyList(),
    val hardcoverId: Long? = null,
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
    val description: String? = null,
    val authors: List<AuthorDto> = emptyList(),
    val coverUrls: List<String> = emptyList(),
    val hardcoverId: Long? = null,
    val hardcover: BookSeriesMetadataDto = BookSeriesMetadataDto(),
)

data class BookGroupNameRequest(
    val name: String,
    val description: String? = null,
)

data class BookGroupOrderRequest(
    val ids: List<Long>,
)

data class BookSeriesSuggestionDto(
    val hardcoverId: Long,
    val name: String,
    val books: List<BookGroupMemberDto>,
    val description: String? = null,
    val hardcover: BookSeriesMetadataDto = BookSeriesMetadataDto(),
)

data class ApplyBookSeriesSuggestionRequest(
    val hardcoverId: Long,
    val seriesId: Long? = null,
    val name: String,
    val bookIds: List<Long>,
    val metadataFields: Set<BookSeriesMetadataField> = emptySet(),
)

data class BookSeriesProviderAuthorDto(
    val id: Long,
    val name: String,
)

data class BookSeriesMetadataDto(
    val slug: String? = null,
    val primaryAuthor: BookSeriesProviderAuthorDto? = null,
    val isCompleted: Boolean? = null,
    val booksCount: Int? = null,
    val primaryBooksCount: Int? = null,
)

enum class BookSeriesMetadataField { NAME, DESCRIPTION, PRIMARY_AUTHOR, PUBLICATION_STATUS, COUNTS, EXTERNAL_SLUG }

data class BookSeriesMetadataRequest(
    val fields: Set<BookSeriesMetadataField>,
)

data class BookSeriesMetadataPreviewDto(
    val current: BookGroupDetailsDto,
    val suggested: BookSeriesSuggestionDto,
)
