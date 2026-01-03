package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.Album
import org.springframework.data.repository.CrudRepository

interface AlbumRepository : CrudRepository<Album, Long> {
    fun findByFingerprint(fingerprint: String): Album?

    fun findByArtistIdAndTitleKeyAndYear(
        artistId: Long,
        titleKey: String,
        year: Int?,
    ): Album?

    fun findFirstByArtistIdAndTitleKeyOrderByIdAsc(
        artistId: Long,
        titleKey: String,
    ): Album?
}
