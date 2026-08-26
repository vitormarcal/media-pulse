package dev.marcal.mediapulse.server.model.music

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import jakarta.persistence.UniqueConstraint
import java.time.Instant

@Entity
@Table(name = "artists", uniqueConstraints = [UniqueConstraint(columnNames = ["fingerprint"])])
data class Artist(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    @Column(nullable = false) val name: String,
    @Column(nullable = false, unique = true) val fingerprint: String,
    @Column(name = "spotify_id", unique = true) val spotifyId: String? = null,
    @Column(name = "musicbrainz_artist_id", unique = true) val musicbrainzArtistId: String? = null,
    @Column(name = "artist_type") val artistType: String? = null,
    @Column(name = "country_code") val countryCode: String? = null,
    @Column(name = "area_name") val areaName: String? = null,
    @Column(name = "begin_area_name") val beginAreaName: String? = null,
    @Column(name = "life_span_begin") val lifeSpanBegin: String? = null,
    @Column(name = "life_span_end") val lifeSpanEnd: String? = null,
    @Column(name = "life_span_ended", nullable = false) val lifeSpanEnded: Boolean = false,
    val disambiguation: String? = null,
    @Column(name = "musicbrainz_synced_at") val musicbrainzSyncedAt: Instant? = null,
    @Column(name = "musicbrainz_sync_error") val musicbrainzSyncError: String? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at") val updatedAt: Instant? = null,
)
