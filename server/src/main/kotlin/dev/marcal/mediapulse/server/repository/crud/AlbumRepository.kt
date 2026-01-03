package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.Album
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AlbumRepository : JpaRepository<Album, Long> {
    fun findByFingerprint(fingerprint: String): Album?

    fun findByArtistIdAndTitleKeyAndYear(
        artistId: Long,
        titleKey: String,
        year: Int,
    ): Album?

    fun findFirstByArtistIdAndTitleKeyAndYearIsNullOrderByIdAsc(
        artistId: Long,
        titleKey: String,
    ): Album?

    fun findFirstByArtistIdAndTitleKeyAndYearIsNotNullOrderByYearAscIdAsc(
        artistId: Long,
        titleKey: String,
    ): Album?

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query(
        """
        UPDATE Album a
           SET a.year = :year,
               a.updatedAt = CURRENT_TIMESTAMP
         WHERE a.id = :albumId
           AND a.year IS NULL
        """,
    )
    fun promoteNullYear(
        @Param("albumId") albumId: Long,
        @Param("year") year: Int,
    ): Int
}
