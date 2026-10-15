package dev.marcal.mediapulse.server.model.music

enum class AlbumArtistSource { LEGACY, SPOTIFY, MUSICBRAINZ_CONFIRMED }

data class AlbumArtistCredit(
    val albumId: Long,
    val position: Int,
    val artistId: Long,
    val creditedName: String,
    val joinPhrase: String = "",
    val source: AlbumArtistSource,
)
