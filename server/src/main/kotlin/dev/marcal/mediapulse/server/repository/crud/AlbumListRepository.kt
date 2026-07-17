package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.AlbumList
import org.springframework.data.jpa.repository.JpaRepository

interface AlbumListRepository : JpaRepository<AlbumList, Long> {
    fun findBySlug(slug: String): AlbumList?

    fun findByNormalizedName(normalizedName: String): AlbumList?
}
