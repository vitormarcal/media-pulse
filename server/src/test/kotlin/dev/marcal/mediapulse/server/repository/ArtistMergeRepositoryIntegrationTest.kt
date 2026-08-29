package dev.marcal.mediapulse.server.repository

import org.flywaydb.core.Flyway
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.transaction.support.TransactionTemplate
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class ArtistMergeRepositoryIntegrationTest {
    @Test
    fun `merge preserves relationships choices and historical aliases`() {
        insertFixture()
        val repository = ArtistMergeRepository(jdbc)

        val result =
            TransactionTemplate(DataSourceTransactionManager(dataSource)).execute {
                repository.merge(
                    ArtistMergeRepository.MergeCommand(
                        targetId = 1,
                        sourceIds = listOf(2),
                        nameId = 1,
                        imageId = 1,
                        musicBrainzId = 1,
                        ratingId = 2,
                        aliasIds = listOf(2),
                        spotifyId = null,
                        musicBrainzValue = null,
                        fingerprint = "target-fingerprint",
                    ),
                )
            }!!

        assertEquals(1, result.movedAlbums)
        assertEquals(1, result.movedTracks)
        assertEquals(1, result.movedComments)
        assertEquals(1, result.mergedGenres)
        assertEquals(2, result.storedAliases)
        assertEquals(1, count("SELECT COUNT(*) FROM artists WHERE id=1"))
        assertEquals(0, count("SELECT COUNT(*) FROM artists WHERE id=2"))
        assertEquals(2, count("SELECT COUNT(*) FROM albums WHERE artist_id=1 AND title='Same album' AND year=2020"))
        assertEquals(2, count("SELECT COUNT(*) FROM tracks WHERE artist_id=1"))
        assertEquals(1, count("SELECT COUNT(*) FROM media_comments WHERE entity_type='ARTIST' AND entity_id=1"))
        assertEquals(1, count("SELECT COUNT(*) FROM artist_term_assignments WHERE artist_id=1"))
        assertEquals(4, count("SELECT rating FROM media_ratings WHERE entity_type='ARTIST' AND entity_id=1"))
        assertEquals(1, count("SELECT COUNT(*) FROM artist_name_aliases WHERE artist_id=1 AND name='Source artist'"))
        assertEquals(1, count("SELECT COUNT(*) FROM artist_name_aliases WHERE artist_id=1 AND name='Historical source name'"))
    }

    private fun insertFixture() {
        jdbc.jdbcTemplate.execute(
            """
            INSERT INTO artists(id,name,fingerprint) VALUES
              (1,'Target artist','target-fingerprint'),
              (2,'Source artist','source-fingerprint');
            INSERT INTO albums(id,artist_id,title,title_key,year,fingerprint) VALUES
              (10,1,'Same album','same-album',2020,'target-album'),
              (11,2,'Same album','same-album',2020,'source-album');
            INSERT INTO tracks(id,artist_id,title,fingerprint) VALUES
              (20,1,'Target track','target-track'),
              (21,2,'Source track','source-track');
            INSERT INTO media_comments(entity_type,entity_id,body,commented_at) VALUES ('ARTIST',2,'comment',NOW());
            INSERT INTO media_ratings(entity_type,entity_id,rating) VALUES ('ARTIST',2,4);
            INSERT INTO album_terms(id,name,normalized_name,slug,kind,source) VALUES (30,'Rock','rock','rock','GENRE','USER');
            INSERT INTO artist_term_assignments(artist_id,term_id,source) VALUES (2,30,'USER');
            INSERT INTO artist_name_aliases(artist_id,name,name_key) VALUES (2,'Historical source name','historical-key');
            """.trimIndent(),
        )
    }

    private fun count(sql: String): Int = jdbc.jdbcTemplate.queryForObject(sql, Int::class.java)!!

    companion object {
        @Container
        @JvmField
        val postgres = PostgreSQLContainer("postgres:16-alpine")

        private lateinit var dataSource: DriverManagerDataSource
        private lateinit var jdbc: NamedParameterJdbcTemplate

        @BeforeAll
        @JvmStatic
        fun migrate() {
            dataSource = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
            Flyway
                .configure()
                .dataSource(dataSource)
                .load()
                .migrate()
            jdbc = NamedParameterJdbcTemplate(dataSource)
        }
    }
}
