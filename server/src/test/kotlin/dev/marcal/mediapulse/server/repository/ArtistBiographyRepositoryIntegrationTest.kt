package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.music.ArtistBiographyDto
import dev.marcal.mediapulse.server.api.music.WikipediaIntroductionDto
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
class ArtistBiographyRepositoryIntegrationTest {
    @Test
    fun `migration persists optional biography and hides stale identity before cascading deletion`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val jdbc = JdbcTemplate(source)
        val repository = ArtistBiographyRepository(jdbc, JacksonConfig().objectMapper())
        val id =
            jdbc.queryForObject(
                "INSERT INTO artists(name, fingerprint, musicbrainz_artist_id) VALUES ('Artist', 'artist', 'mbid') RETURNING id",
                Long::class.java,
            )!!
        assertNull(repository.find(id))
        val text =
            WikipediaIntroductionDto(
                "Biografia com acentuação e 'aspas'.",
                "pt",
                "https://pt.wikipedia.org/wiki/Artist",
                42L,
                "CC BY-SA 4.0",
                "https://creativecommons.org/licenses/by-sa/4.0/",
            )
        val snapshot = ArtistBiographyDto("mbid", text, Instant.parse("2026-11-12T12:00:00.123456789Z"))
        repository.save(id, snapshot)
        assertEquals(snapshot, repository.find(id))
        assertEquals(snapshot, repository.current(id))
        jdbc.update("UPDATE artists SET musicbrainz_artist_id = 'another' WHERE id = ?", id)
        assertNull(repository.current(id))
        assertEquals(snapshot, repository.find(id))
        repository.save(id, snapshot.copy(artistMbid = "another"))
        assertEquals("another", repository.current(id)?.artistMbid)
        jdbc.update("DELETE FROM artists WHERE id = ?", id)
        assertNull(repository.find(id))
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
