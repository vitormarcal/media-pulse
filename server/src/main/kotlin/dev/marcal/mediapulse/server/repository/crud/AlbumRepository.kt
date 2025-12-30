package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.Album
import org.springframework.data.repository.CrudRepository

interface AlbumRepository : CrudRepository<Album, Long> {
    fun findByFingerprint(fingerprint: String): Album?

    fun findByArtistIdAndTitleAndYear(
        artistId: Long,
        title: String,
        year: Int?,
    ): Album?

    fun findAllByArtistIdAndTitle(
        artistId: Long,
        title: String,
    ): List<Album>
}
