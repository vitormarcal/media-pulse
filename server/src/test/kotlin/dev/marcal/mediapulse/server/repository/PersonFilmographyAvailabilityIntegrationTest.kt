package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.movies.PersonFilmographySyncState
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.MediaType
import dev.marcal.mediapulse.server.service.movie.ManualMovieCatalogService
import dev.marcal.mediapulse.server.service.movie.MovieCreditsService
import dev.marcal.mediapulse.server.service.person.PersonFilmographyService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class PersonFilmographyAvailabilityIntegrationTest {
    @Test
    fun `reuse restoration failures retention and favorites use persisted sync state`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val jdbc = JdbcTemplate(source)
        jdbc.update(
            "INSERT INTO people(id, tmdb_id, name, normalized_name, slug) VALUES (44, '138', 'Person', 'person', 'person'), (45, '139', 'Favorite', 'favorite', 'favorite')",
        )
        val factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                setPackagesToScan("dev.marcal.mediapulse.server.model.person")
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "validate"))
                afterPropertiesSet()
            }
        val manager = factory.`object`!!.createEntityManager()
        try {
            val repository = PersonFilmographyRepository(manager)
            val tmdb = mockk<TmdbApiClient>()
            val tx = mockk<TxUtil>()
            every { tx.inTx<Any>(any()) } answers {
                manager.transaction.begin()
                try {
                    firstArg<() -> Any>().invoke().also { manager.transaction.commit() }
                } catch (ex: Exception) {
                    manager.transaction.rollback()
                    throw ex
                }
            }
            val service = PersonFilmographyService(repository, tmdb, mockk<ManualMovieCatalogService>(), tx, mockk<MovieCreditsService>())
            every { tmdb.fetchPersonMovieCredits("138") } returns TmdbApiClient.TmdbPersonMovieCredits(emptyList(), emptyList())
            assertFalse(repository.findSyncStatus(44, MediaType.MOVIE).synced)
            assertEquals(PersonFilmographySyncState.COMPLETE, service.ensureFilmography(44).syncState)
            val originalSync = jdbc.queryForMap("SELECT synced_at FROM person_filmography_syncs WHERE person_id = 44")["synced_at"]
            service.ensureFilmography(44)
            assertEquals(originalSync, jdbc.queryForMap("SELECT synced_at FROM person_filmography_syncs WHERE person_id = 44")["synced_at"])
            verify(exactly = 1) { tmdb.fetchPersonMovieCredits("138") }
            assertFalse(repository.findSyncStatus(44, MediaType.SHOW).synced)

            jdbc.update("UPDATE people SET favorited_at = NOW() WHERE id = 45")
            jdbc.update(
                "INSERT INTO person_filmography_syncs(person_id, media_type, synced_at) VALUES (45, 'MOVIE', NOW() - INTERVAL '8 days')",
            )
            jdbc.update("UPDATE person_filmography_syncs SET synced_at = NOW() - INTERVAL '8 days' WHERE person_id = 44")
            assertEquals(PersonFilmographySyncState.COMPLETE, service.ensureFilmography(44).syncState)
            verify(exactly = 1) { tmdb.fetchPersonMovieCredits("138") }
            assertEquals(listOf(44L), repository.findCompactionCandidatePersonIds(100))
            tx.inTx { assertNotNull(repository.compactIfEligible(44, MediaType.MOVIE)) }
            assertTrue(repository.findSyncStatus(44, MediaType.MOVIE).compacted)
            val beforeFailure = jdbc.queryForMap("SELECT synced_at FROM person_filmography_syncs WHERE person_id = 44")["synced_at"]
            every { tmdb.fetchPersonMovieCredits("138") } returns null
            assertEquals(PersonFilmographySyncState.FAILED, service.ensureFilmography(44).syncState)
            assertEquals(
                beforeFailure,
                jdbc.queryForMap("SELECT synced_at FROM person_filmography_syncs WHERE person_id = 44")["synced_at"],
            )
            assertFalse(repository.findSyncStatus(44, MediaType.MOVIE).retryReady)
            service.ensureFilmography(44)
            verify(exactly = 2) { tmdb.fetchPersonMovieCredits("138") }

            every { tmdb.fetchPersonMovieCredits("138") } returns TmdbApiClient.TmdbPersonMovieCredits(emptyList(), emptyList())
            assertEquals(PersonFilmographySyncState.COMPLETE, service.refreshAndGetFilmography(44).syncState)
            assertFalse(repository.findSyncStatus(44, MediaType.MOVIE).compacted)
            assertFalse(repository.findSyncStatus(44, MediaType.MOVIE).failed)
            assertTrue(repository.findCompactionCandidatePersonIds(100).isEmpty())
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
