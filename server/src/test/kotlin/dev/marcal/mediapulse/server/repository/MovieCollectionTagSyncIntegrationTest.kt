package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.config.VisualLibraryProperties
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.service.movie.MovieCollectionTagSyncService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.util.UUID
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class MovieCollectionTagSyncIntegrationTest {
    @Test
    fun `backfill covers shared assets and collection corrections with leases failures and rollback`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .target("76")
            .load()
            .migrate()
        val jdbc = JdbcTemplate(source)
        val asset = UUID.randomUUID()
        val other = UUID.randomUUID()
        val remote = "/api/image-assets/$asset/content"
        jdbc.update("INSERT INTO movie_collections(id,tmdb_id,name) VALUES (1,'10',' First   collection ')")
        jdbc.update(
            "INSERT INTO movies(id,original_title,fingerprint,cover_url,collection_id) VALUES (1,'One','one',?,1),(2,'Two','two',?,1),(3,'Local','local','/covers/local.png',NULL)",
            remote,
            remote,
        )
        jdbc.update(
            "INSERT INTO movie_images(movie_id,url,is_primary) VALUES (1,?,true),(1,?,false)",
            remote,
            "/api/image-assets/$other/content",
        )
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val repo = MovieCollectionTagSyncRepository(jdbc)
        val tx = TxUtil(DataSourceTransactionManager(source))
        val client = mockk<VisualLibraryClient>()
        val service = MovieCollectionTagSyncService(repo, client, VisualLibraryProperties(baseUrl = "http://visual.test"), tx)
        every { client.synchronizeMovieCollection(any(), any(), any(), any()) } returns
            VisualLibraryClient.MovementSyncResponse(listOf("personal"))
        assertTrue(service.state(1).pending)
        assertFalse(service.state(3).hasRemoteImages)
        service.runBatch()
        assertFalse(service.state(1).pending)
        assertTrue(service.state(1).message!!.contains("preservadas"))
        verify(exactly = 1) { client.synchronizeMovieCollection(asset, 1, listOf("First collection"), 1) }
        verify(exactly = 1) { client.synchronizeMovieCollection(other, 1, listOf("First collection"), 1) }
        verify(exactly = 1) { client.synchronizeMovieCollection(asset, 2, listOf("First collection"), 1) }

        val revision = service.state(1).revision
        jdbc.update("UPDATE movie_collections SET name='Renamed' WHERE id=1")
        assertTrue(service.state(1).revision > revision)
        every { client.synchronizeMovieCollection(other, 1, any(), any()) } throws
            ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Limite de tags")
        service.runBatch()
        assertTrue(service.state(1).pending)
        assertEquals("Limite de tags", service.state(1).message)
        every { client.synchronizeMovieCollection(other, 1, any(), any()) } returns VisualLibraryClient.MovementSyncResponse()
        service.retry(1)
        service.runBatch()
        assertFalse(service.state(1).pending)

        jdbc.update("UPDATE movie_collections SET name=? WHERE id=1", "x".repeat(49))
        service.runBatch()
        assertTrue(service.state(1).message!!.contains("48"))
        jdbc.update("UPDATE movie_collections SET name='Valid' WHERE id=1")
        val stale = tx.inTx { repo.claim(1) }!!
        assertNull(tx.inTx { listOfNotNull(repo.claim(1)) }.firstOrNull())
        jdbc.update("UPDATE movies SET collection_id=NULL WHERE id=1")
        assertFalse(tx.inTx { repo.finish(stale, false, null) })
        service.runBatch()
        verify { client.synchronizeMovieCollection(asset, 1, emptyList(), any()) }
        assertFalse(service.state(1).pending)

        val before = service.state(1).revision
        assertFailsWith<IllegalStateException> {
            tx.inTx {
                jdbc.update("UPDATE movies SET collection_id=1 WHERE id=1")
                error("rollback")
            }
        }
        assertEquals(before, service.state(1).revision)
        jdbc.update("INSERT INTO movie_images(movie_id,url) VALUES (1,?)", "/api/image-assets/${UUID.randomUUID()}/content")
        assertTrue(service.state(1).pending)
        val claim = tx.inTx { repo.claim(1) }!!
        jdbc.update("UPDATE movies SET collection_tags_claim_until=now()-INTERVAL '1 minute' WHERE id=1")
        assertNotNull(tx.inTx { repo.claim(1) })
        assertFalse(tx.inTx { repo.finish(claim, false, null) })
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
