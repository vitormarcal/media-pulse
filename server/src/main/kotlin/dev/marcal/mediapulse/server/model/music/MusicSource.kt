package dev.marcal.mediapulse.server.model.music

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.apache.commons.codec.digest.DigestUtils
import java.time.Instant

@Entity
@Table(
    name = "music_sources",
)
data class MusicSource(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val title: String,
    val album: String,
    val artist: String,
    val year: Int,
    @Column(unique = true)
    val fingerprint: String = generateFingerprint(title, album, artist, year),
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
) {
    companion object {
        fun generateFingerprint(
            title: String,
            album: String,
            artist: String,
            year: Int,
        ): String {
            val input = "$title|$album|$artist|$year"
            return DigestUtils.sha256Hex(input)
        }
    }
}
