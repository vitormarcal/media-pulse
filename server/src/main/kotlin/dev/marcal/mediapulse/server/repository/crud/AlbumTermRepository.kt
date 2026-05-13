package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.AlbumTerm
import dev.marcal.mediapulse.server.model.music.AlbumTermKind
import org.springframework.data.repository.CrudRepository

interface AlbumTermRepository : CrudRepository<AlbumTerm, Long> {
    fun findByKindAndNormalizedName(
        kind: AlbumTermKind,
        normalizedName: String,
    ): AlbumTerm?
}
