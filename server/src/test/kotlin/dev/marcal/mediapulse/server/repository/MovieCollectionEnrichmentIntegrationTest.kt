package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.repository.crud.MovieAutomaticEnrichmentRepository
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertNull

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class MovieCollectionEnrichmentIntegrationTest {
    @Test
    fun `worker recovers missing collection and respects completion and retry delay`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .target("74")
            .load()
            .migrate()
        val jdbc = JdbcTemplate(source)
        jdbc.update(
            """
            INSERT INTO movies(id, original_title, fingerprint, tmdb_id, terms_synced_at, credits_synced_at, companies_synced_at)
            VALUES (1, 'Movie', 'movie-1', '603', NOW(), NOW(), NOW())
            """.trimIndent(),
        )
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                setPackagesToScan("dev.marcal.mediapulse.server.model.movie")
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "validate"))
                afterPropertiesSet()
            }
        val manager = factory.`object`!!.createEntityManager()
        try {
            val repository = MovieAutomaticEnrichmentRepository(manager)
            assertEquals(listOf(1L), repository.findPendingMovieIds(25))
            manager.transaction.begin()
            repository.markCollectionFailure(1, "Unavailable")
            manager.transaction.commit()
            assertEquals(emptyList(), repository.findPendingMovieIds(25))
            jdbc.update("UPDATE movies SET collection_sync_attempted_at = NOW() - INTERVAL '2 days' WHERE id = 1")
            assertEquals(listOf(1L), repository.findPendingMovieIds(25))
            manager.transaction.begin()
            repository.markCollectionComplete(1)
            manager.transaction.commit()
            assertEquals(emptyList(), repository.findPendingMovieIds(25))
            assertNull(jdbc.queryForMap("SELECT collection_sync_error FROM movies WHERE id = 1")["collection_sync_error"])
            assertEquals("Movie", jdbc.queryForObject("SELECT original_title FROM movies WHERE id = 1", String::class.java))
        } finally {
            manager.close()
            factory.destroy()
        }
    }

    companion object {
        @Container
        @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
