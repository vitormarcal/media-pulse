package dev.marcal.mediapulse.server.repository.crud

import com.fasterxml.jackson.databind.ObjectMapper
import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillBatch
import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillItem
import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Repository
class AlbumCreditBackfillRepository(
    private val jdbc: JdbcTemplate,
    private val mapper: ObjectMapper,
) {
    data class CatalogAlbum(
        val id: Long,
        val title: String,
        val revision: Long,
        val releaseGroupId: String?,
        val spotifyId: String?,
    )

    fun targets(limit: Int): List<Long> =
        jdbc.queryForList(
            """SELECT al.id FROM albums al
           WHERE NOT EXISTS (SELECT 1 FROM album_artists aa WHERE aa.album_id = al.id AND aa.source <> 'LEGACY')
             AND (al.musicbrainz_release_group_id IS NOT NULL OR EXISTS (SELECT 1 FROM album_spotify_ids s WHERE s.album_id = al.id))
           ORDER BY al.id LIMIT ?""",
            Long::class.java,
            limit,
        )

    fun album(albumId: Long): CatalogAlbum? =
        jdbc
            .query(
                """SELECT id, title, artist_credits_revision, musicbrainz_release_group_id,
           (SELECT MIN(spotify_id) FROM album_spotify_ids WHERE album_id = al.id)
           FROM albums al WHERE id = ?""",
                { rs, _ -> CatalogAlbum(rs.getLong(1), rs.getString(2), rs.getLong(3), rs.getString(4), rs.getString(5)) },
                albumId,
            ).firstOrNull()

    fun isLinked(
        albumId: Long,
        provider: AlbumArtistSource,
        externalId: String,
    ): Boolean =
        if (provider == AlbumArtistSource.MUSICBRAINZ_CONFIRMED) {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM albums WHERE id = ? AND musicbrainz_release_group_id = ?",
                Long::class.java,
                albumId,
                externalId,
            ) ==
                1L
        } else {
            jdbc.queryForObject(
                "SELECT COUNT(*) FROM album_spotify_ids WHERE album_id = ? AND spotify_id = ?",
                Long::class.java,
                albumId,
                externalId,
            ) ==
                1L
        }

    fun create(): UUID = UUID.randomUUID().also { jdbc.update("INSERT INTO album_credit_backfill_batches(id) VALUES (?)", it) }

    fun save(
        batchId: UUID,
        item: AlbumCreditBackfillItem,
    ) {
        jdbc.update(
            """INSERT INTO album_credit_backfill_items(batch_id, album_id, status, document) VALUES (?, ?, ?, CAST(? AS jsonb))
               ON CONFLICT (batch_id, album_id) DO UPDATE SET status = EXCLUDED.status, document = EXCLUDED.document, updated_at = NOW()""",
            batchId,
            item.albumId,
            item.status.name,
            mapper.writeValueAsString(item),
        )
    }

    fun lockItem(
        batchId: UUID,
        albumId: Long,
    ): AlbumCreditBackfillItem =
        jdbc.queryForObject(
            "SELECT document::text FROM album_credit_backfill_items WHERE batch_id = ? AND album_id = ? FOR UPDATE",
            { rs, _ -> mapper.readValue(rs.getString(1), AlbumCreditBackfillItem::class.java) },
            batchId,
            albumId,
        )!!

    fun recent(): List<AlbumCreditBackfillBatch> =
        jdbc
            .queryForList(
                "SELECT id FROM album_credit_backfill_batches ORDER BY created_at DESC, id DESC LIMIT 20",
                UUID::class.java,
            ).map(::batch)

    fun batch(batchId: UUID): AlbumCreditBackfillBatch {
        val createdAt =
            jdbc
                .query(
                    "SELECT created_at FROM album_credit_backfill_batches WHERE id = ?",
                    { rs, _ -> rs.getTimestamp(1).toInstant() },
                    batchId,
                ).firstOrNull() ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Lote não encontrado")
        val items =
            jdbc.query(
                "SELECT document::text FROM album_credit_backfill_items WHERE batch_id = ? ORDER BY album_id",
                { rs, _ -> mapper.readValue(rs.getString(1), AlbumCreditBackfillItem::class.java) },
                batchId,
            )
        return AlbumCreditBackfillBatch(batchId, createdAt, items)
    }
}
