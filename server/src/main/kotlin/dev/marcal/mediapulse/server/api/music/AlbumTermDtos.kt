package dev.marcal.mediapulse.server.api.music

enum class AlbumTermKindDto {
    GENRE,
    TAG,
}

enum class AlbumTermSourceDto {
    USER,
}

data class AlbumTermDto(
    val id: Long,
    val name: String,
    val slug: String,
    val kind: AlbumTermKindDto,
    val source: AlbumTermSourceDto,
    val hiddenGlobally: Boolean,
    val hiddenForAlbum: Boolean,
    val active: Boolean,
)

data class AlbumTermSuggestionDto(
    val id: Long,
    val name: String,
    val slug: String,
    val kind: AlbumTermKindDto,
    val source: AlbumTermSourceDto,
    val hiddenGlobally: Boolean,
)

data class AlbumTermCreateRequest(
    val name: String,
    val kind: AlbumTermKindDto,
)

data class AlbumTermVisibilityRequest(
    val hidden: Boolean,
)

data class AlbumTermDetailsResponse(
    val termId: Long,
    val name: String,
    val slug: String,
    val kind: AlbumTermKindDto,
    val source: AlbumTermSourceDto,
    val albumCount: Long,
    val playedAlbumsCount: Long,
    val albums: List<AlbumLibraryRow>,
)
