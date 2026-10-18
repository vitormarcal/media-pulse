package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.books.ApplyBookSeriesSuggestionRequest
import dev.marcal.mediapulse.server.api.books.BookSeriesMetadataField
import dev.marcal.mediapulse.server.api.books.BookSeriesSuggestionDto
import dev.marcal.mediapulse.server.config.VisualLibraryProperties
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.book.BookGroupKind.SERIES
import dev.marcal.mediapulse.server.service.books.BookSeriesService
import dev.marcal.mediapulse.server.service.books.BookSeriesTagSyncService
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
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
class BookSeriesTagSyncIntegrationTest {
    @Test
    fun `backfill covers every reference and invalidations preserve revisions failures and archive`() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .target("73")
            .load()
            .migrate()
        val jdbc = JdbcTemplate(source)
        val asset = UUID.randomUUID()
        val remote = "/api/image-assets/$asset/content"
        jdbc.update(
            "INSERT INTO books(id,title,slug,fingerprint,cover_url) VALUES (1,'One','one','one',?), (2,'Local','local','local','/covers/local.png')",
            remote,
        )
        jdbc.update(
            "INSERT INTO book_editions(id,book_id,fingerprint,cover_url) VALUES (11,1,'first',?), (12,1,'second',?)",
            remote,
            remote,
        )
        jdbc.update("INSERT INTO book_series(id,name,hardcover_id) VALUES (1,' First   series ',99),(2,'Second',NULL)")
        jdbc.update("INSERT INTO book_series_items(series_id,book_id,position) VALUES (1,1,2),(2,1,1)")
        jdbc.update("INSERT INTO book_reads(book_id,source,source_session_id,status) VALUES (1,'HARDCOVER',1,'READ')")
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        val repo = BookSeriesTagSyncRepository(jdbc)
        val tx = TxUtil(DataSourceTransactionManager(source))
        val client = mockk<VisualLibraryClient>()
        val service = BookSeriesTagSyncService(repo, client, VisualLibraryProperties(baseUrl = "http://visual.test"), tx)
        val groups = BookSeriesService(BookSeriesRepository(jdbc))
        every { client.synchronizeBookSeries(any(), any(), any(), any(), any()) } returns VisualLibraryClient.MovementSyncResponse()
        assertTrue(service.state(1).pending)
        assertFalse(service.state(2).hasRemoteCovers)
        assertFalse(service.state(2).pending)
        service.runBatch()
        assertFalse(service.state(1).pending)
        verify(exactly = 1) { client.synchronizeBookSeries(asset, "book", 1, listOf("First series", "Second"), 1) }
        verify(exactly = 1) { client.synchronizeBookSeries(asset, "book_edition", 11, listOf("First series", "Second"), 1) }
        verify(exactly = 1) { client.synchronizeBookSeries(asset, "book_edition", 12, listOf("First series", "Second"), 1) }
        val unchanged = service.state(1).revision
        tx.inTx { groups.add(SERIES, 1, 1) }
        tx.inTx { groups.order(SERIES, 1, listOf(1)) }
        assertEquals(unchanged, service.state(1).revision)
        tx.inTx { groups.rename(SERIES, 1, "Renamed") }
        val first = tx.inTx { listOfNotNull(repo.claim(1)) }.single()
        assertEquals(listOf("Renamed", "Second"), first.seriesNames)
        tx.inTx { groups.remove(SERIES, 2, 1) }
        val second = tx.inTx { listOfNotNull(repo.claim(1)) }.single()
        assertTrue(second.revision > first.revision)
        assertFalse(tx.inTx { repo.finish(first, false, null) })
        assertFalse(tx.inTx { repo.finish(first, true, "stale error") })
        assertTrue(service.state(1).pending)
        assertTrue(tx.inTx { repo.finish(second, false, null) })
        assertNull(service.state(1).message)
        // Snapshot lease expiry and replacement claims at the same revision use distinct tokens.
        tx.inTx { repo.retry(1) }
        val oldClaim = tx.inTx { listOfNotNull(repo.claim(1)) }.single()
        jdbc.update("UPDATE books SET series_tags_claim_until = now() - INTERVAL '1 second' WHERE id=1")
        val newClaim = tx.inTx { listOfNotNull(repo.claim(1)) }.single()
        assertEquals(oldClaim.revision, newClaim.revision)
        assertFalse(tx.inTx { repo.renew(oldClaim) })
        assertTrue(tx.inTx { repo.renew(newClaim) })
        assertFalse(tx.inTx { repo.finish(oldClaim, false, null) })
        assertTrue(tx.inTx { repo.finish(newClaim, false, null) })
        // Hardcover-selected names go through the same durable invalidation path.
        val suggested = BookSeriesSuggestionDto(99, "Hardcover name", BookSeriesRepository(jdbc).items(SERIES, 1))
        tx.inTx {
            groups.apply(
                suggested,
                ApplyBookSeriesSuggestionRequest(99, 1, "Ignored", listOf(1), setOf(BookSeriesMetadataField.NAME)),
            )
        }
        assertTrue(service.state(1).pending)
        every { client.synchronizeBookSeries(any(), "book_edition", 11, any(), any()) } throws
            ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Tag limit")
        service.synchronize(1)
        assertTrue(service.state(1).pending)
        assertEquals("Tag limit", service.state(1).message)
        assertFalse(repo.candidates(100).contains(1))
        service.retry(1)
        assertNull(service.state(1).message)
        every { client.synchronizeBookSeries(any(), "book_edition", 11, any(), any()) } returns VisualLibraryClient.MovementSyncResponse()
        service.runBatch()
        assertFalse(service.state(1).pending)
        // All cover write paths, including raw native updates used by import and migration, enqueue.
        val beforeCover = service.state(1).revision
        jdbc.update("UPDATE book_editions SET cover_url = '/covers/new.png' WHERE id=12")
        jdbc.update("UPDATE books SET cover_url = '/covers/new.png' WHERE id=1")
        assertEquals(beforeCover + 2, service.state(1).revision)
        jdbc.update("INSERT INTO book_editions(id,book_id,fingerprint,cover_url) VALUES (13,1,'third',?)", remote)
        assertTrue(service.state(1).pending)
        // Tag validation failures do not roll back the local series edit or change any cover.
        tx.inTx { groups.rename(SERIES, 1, "x".repeat(49)) }
        service.synchronize(1)
        assertTrue(service.state(1).message!!.contains("48"))
        assertEquals("x".repeat(49), groups.details(SERIES, 1).name)
        tx.inTx { groups.rename(SERIES, 1, "Valid") }
        service.synchronize(1)
        assertFalse(service.state(1).pending)
        tx.inTx { groups.delete(SERIES, 1) }
        service.synchronize(1)
        assertFalse(service.state(1).pending)
        verify { client.synchronizeBookSeries(asset, "book_edition", 11, emptyList(), any()) }
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM book_reads", Int::class.java))
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM books", Int::class.java))
        assertNotNull(jdbc.queryForObject("SELECT cover_url FROM book_editions WHERE id=11", String::class.java))
        // A transaction rollback also rolls back the revision; no uncommitted change is sent.
        val revision = service.state(1).revision
        runCatching {
            tx.inTx {
                jdbc.update("UPDATE books SET cover_url = ? WHERE id=1", remote)
                error("rollback")
            }
        }
        assertEquals(revision, service.state(1).revision)
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
