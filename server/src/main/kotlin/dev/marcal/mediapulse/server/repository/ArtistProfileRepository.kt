package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.music.AlbumTermDto
import dev.marcal.mediapulse.server.api.music.AlbumTermKindDto
import dev.marcal.mediapulse.server.api.music.AlbumTermSourceDto
import dev.marcal.mediapulse.server.api.music.ArtistAliasDto
import dev.marcal.mediapulse.server.api.music.ArtistExternalLinkDto
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistAlias
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.stereotype.Repository

@Repository
class ArtistProfileRepository(
    private val jdbc: JdbcTemplate,
) {
    fun replaceAliases(
        artistId: Long,
        aliases: List<MbArtistAlias>,
    ) {
        jdbc.update("DELETE FROM artist_aliases WHERE artist_id = ?", artistId)
        aliases.distinctBy { it.name.trim().lowercase() to it.locale }.forEach {
            jdbc.update(
                """INSERT INTO artist_aliases(artist_id, name, locale, sort_name, alias_type, is_primary)
                   VALUES (?, ?, ?, ?, ?, ?)""",
                artistId,
                it.name.trim(),
                it.locale,
                it.sortName,
                it.type,
                it.primary == true,
            )
        }
    }

    fun replaceLinks(
        artistId: Long,
        links: Map<String, String>,
    ) {
        jdbc.update("DELETE FROM artist_external_links WHERE artist_id = ?", artistId)
        links.forEach { (type, url) ->
            jdbc.update(
                "INSERT INTO artist_external_links(artist_id, link_type, url) VALUES (?, ?, ?)",
                artistId,
                type,
                url,
            )
        }
    }

    fun aliases(artistId: Long): List<ArtistAliasDto> =
        jdbc.query(
            """SELECT name, locale, sort_name, alias_type, is_primary FROM artist_aliases
               WHERE artist_id = ? ORDER BY is_primary DESC, name""",
            { rs, _ -> ArtistAliasDto(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getBoolean(5)) },
            artistId,
        )

    fun links(artistId: Long): List<ArtistExternalLinkDto> =
        jdbc.query(
            "SELECT link_type, url FROM artist_external_links WHERE artist_id = ? ORDER BY link_type",
            { rs, _ -> ArtistExternalLinkDto(rs.getString(1), rs.getString(2)) },
            artistId,
        )

    fun upsertGenre(
        artistId: Long,
        termId: Long,
        source: String,
        restore: Boolean = false,
    ) {
        jdbc.update(
            """INSERT INTO artist_term_assignments(artist_id, term_id, source, hidden, updated_at)
               VALUES (?, ?, ?, FALSE, NOW())
               ON CONFLICT (artist_id, term_id) DO UPDATE SET updated_at = NOW()""",
            artistId,
            termId,
            source,
        )
        if (restore) updateGenreVisibility(artistId, termId, false)
    }

    fun updateGenreVisibility(
        artistId: Long,
        termId: Long,
        hidden: Boolean,
    ): Int =
        jdbc.update(
            "UPDATE artist_term_assignments SET hidden = ?, updated_at = NOW() WHERE artist_id = ? AND term_id = ?",
            hidden,
            artistId,
            termId,
        )

    fun genres(artistId: Long): List<AlbumTermDto> =
        jdbc.query(
            """SELECT t.id, t.name, t.slug, a.source, t.hidden, a.hidden
               FROM artist_term_assignments a JOIN album_terms t ON t.id = a.term_id
               WHERE a.artist_id = ? AND t.kind = 'GENRE'
               ORDER BY CASE WHEN t.hidden OR a.hidden THEN 1 ELSE 0 END, t.name""",
            { rs, _ ->
                AlbumTermDto(
                    id = rs.getLong(1),
                    name = rs.getString(2),
                    slug = rs.getString(3),
                    kind = AlbumTermKindDto.GENRE,
                    source = AlbumTermSourceDto.valueOf(rs.getString(4)),
                    hiddenGlobally = rs.getBoolean(5),
                    hiddenForAlbum = rs.getBoolean(6),
                    active = !rs.getBoolean(5) && !rs.getBoolean(6),
                )
            },
            artistId,
        )
}
