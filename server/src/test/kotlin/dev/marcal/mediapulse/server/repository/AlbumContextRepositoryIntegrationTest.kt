package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.music.AlbumContextDto
import dev.marcal.mediapulse.server.api.music.AlbumIntroductionDto
import dev.marcal.mediapulse.server.config.JacksonConfig
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class AlbumContextRepositoryIntegrationTest {
    @Test
    fun `migration supports optional context exact JSON round trip replacement and cascade`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val jdbc = JdbcTemplate(source)
        val repository = AlbumContextRepository(jdbc, JacksonConfig().objectMapper())
        val artistId =
            jdbc.queryForObject(
                "INSERT INTO artists(name, fingerprint) VALUES ('Artist', 'artist') RETURNING id",
                Long::class.java,
            )!!
        val albumId =
            jdbc.queryForObject(
                "INSERT INTO albums(artist_id, title, title_key, fingerprint) VALUES (?, 'Album', 'album', 'album') RETURNING id",
                Long::class.java,
                artistId,
            )!!
        assertNull(repository.find(albumId))
        val snapshot =
            AlbumContextDto(
                "mbid",
                "Album",
                listOf("Live"),
                "2001-02",
                mapOf("MusicBrainz" to "https://musicbrainz.org/release-group/mbid"),
                AlbumIntroductionDto(
                    "Texto com acentuação e 'aspas'.",
                    "pt",
                    "https://pt.wikipedia.org/wiki/Album",
                    42L,
                    "CC BY-SA 4.0",
                    "https://creativecommons.org/licenses/by-sa/4.0/",
                ),
                Instant.parse("2026-11-11T12:00:00.123456789Z"),
            )
        repository.save(albumId, snapshot)
        assertEquals(snapshot, repository.find(albumId))
        repository.save(albumId, snapshot.copy(primaryType = "EP"))
        assertEquals("EP", repository.find(albumId)?.primaryType)
        jdbc.update("DELETE FROM albums WHERE id = ?", albumId)
        assertNull(repository.find(albumId))
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
