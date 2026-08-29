package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.Album
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param

interface AlbumRepository : JpaRepository<Album, Long> {
    fun findAllByArtistId(artistId: Long): List<Album>

    fun findByFingerprint(fingerprint: String): Album?

    fun findByMusicbrainzReleaseGroupId(musicbrainzReleaseGroupId: String): Album?

    @Query(
        value =
            """
            SELECT a.* FROM albums a
            JOIN album_musicbrainz_release_group_aliases alias ON alias.album_id = a.id
            WHERE alias.release_group_id = :releaseGroupId
            """,
        nativeQuery = true,
    )
    fun findByMusicbrainzReleaseGroupAlias(
        @Param("releaseGroupId") releaseGroupId: String,
    ): Album?

    @Query(
        value =
            """
            SELECT a.* FROM albums a
            JOIN album_title_aliases alias ON alias.album_id = a.id
            WHERE alias.artist_id = :artistId AND alias.title_key = :titleKey
            ORDER BY a.id
            LIMIT 1
            """,
        nativeQuery = true,
    )
    fun findByTitleAlias(
        @Param("artistId") artistId: Long,
        @Param("titleKey") titleKey: String,
    ): Album?

    @Query(
        value =
            """
            SELECT * FROM albums
            WHERE artist_id = :artistId AND title_key = :titleKey AND year = :year
            ORDER BY id
            LIMIT 1
            """,
        nativeQuery = true,
    )
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
