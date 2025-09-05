package dev.marcal.mediapulse.server.model.series

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
    name = "episode_sources",
)
data class EpisodeSource(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,
    val showTitle: String,
    val seasonNumber: Int,
    val episodeNumber: Int,
    val episodeTitle: String,
    val year: Int,
    @Column(unique = true)
    val fingerprint: String = generateFingerprint(showTitle, seasonNumber, episodeNumber, year),
    @Column(name = "created_at")
    val createdAt: Instant = Instant.now(),
    @Column(name = "updated_at")
    val updatedAt: Instant? = null,
) {
    companion object {
        fun generateFingerprint(
            showTitle: String,
            seasonNumber: Int,
            episodeNumber: Int,
            year: Int,
        ): String {
            val input = "$showTitle|$seasonNumber|$episodeNumber|$year"
            return DigestUtils.sha256Hex(input)
        }
    }
}
