package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.Artist
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.CrudRepository

interface ArtistRepository : CrudRepository<Artist, Long> {
    fun findByFingerprint(fingerprint: String): Artist?

    fun findBySpotifyId(spotifyId: String): Artist?

    fun findByMusicbrainzArtistId(musicbrainzArtistId: String): Artist?

    @Query("SELECT a.* FROM artists a JOIN artist_name_aliases n ON n.artist_id=a.id WHERE n.name_key=:nameKey LIMIT 1", nativeQuery = true)
    fun findByNameAlias(nameKey: String): Artist?
}
