package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.config.VisualLibraryProperties
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.visuallibrary.VisualLibraryUploadMetadata
import dev.marcal.mediapulse.server.repository.crud.LocalImageMigrationRepository
import dev.marcal.mediapulse.server.service.artwork.ArtworkImageService
import dev.marcal.mediapulse.server.service.image.LocalImageMigrationService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.http.ResponseEntity
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DataSourceTransactionManager
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.attribute.PosixFilePermissions
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LocalImageMigrationIntegrationTest {
    private val root = Files.createTempDirectory("media-pulse-image-migration-")
    private val remote = mockk<VisualLibraryClient>()
    private val artworks = mockk<ArtworkImageService>()
    private lateinit var jdbc: JdbcTemplate
    private lateinit var repository: LocalImageMigrationRepository
    private lateinit var tx: TxUtil
    private val asset = "/api/image-assets/00000000-0000-0000-0000-000000000001/content"
    private val bytes =
        ByteArrayOutputStream().use {
            ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", it)
            it.toByteArray()
        }

    @BeforeAll
    fun setup() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        jdbc = JdbcTemplate(source)
        repository = LocalImageMigrationRepository(jdbc)
        tx = TxUtil(DataSourceTransactionManager(source))
    }

    @BeforeEach
    fun reset() {
        jdbc.execute(
            "TRUNCATE local_image_migrations, games, magazine_publications, artworks, artwork_artists, books RESTART IDENTITY CASCADE",
        )
        Files.walk(root).use { paths -> paths.filter { it != root }.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
        clearMocks(remote, artworks)
        every { remote.upload(any(), any()) } returns asset
        every { remote.content(any(), null, null) } returns ResponseEntity.ok(bytes)
        every { artworks.artistMetadata(any()) } answers
            { VisualLibraryUploadMetadata.forEntity("artwork_artist", firstArg(), "artista", "Artista") }
        every { artworks.artworkMetadata(any()) } answers {
            VisualLibraryUploadMetadata
                .forEntity("artwork", firstArg(), "arte visual", "Obra")
                .copy(movements = listOf("Simbolismo"), movementsRevision = 1)
        }
    }

    @AfterAll
    fun close() {
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    private fun service(
        enabled: Boolean = true,
        limit: Int = 10,
    ) = LocalImageMigrationService(
        VisualLibraryProperties(baseUrl = "http://visual-library:3000", migrationEnabled = enabled, migrationBatchSize = limit),
        repository,
        remote,
        artworks,
        tx,
        root.toString(),
    )

    private fun file(name: String): String {
        val path = root.resolve(name)
        Files.createDirectories(path.parent)
        Files.write(path, bytes)
        return "/covers/$name"
    }

    private fun exists(url: String) = Files.exists(root.resolve(url.removePrefix("/covers/")))

    private fun game(url: String): Long =
        jdbc.queryForObject(
            "INSERT INTO games(title, original_title, fingerprint, cover_url) VALUES ('Jogo', 'Jogo', ?, ?) RETURNING id",
            Long::class.java,
            java.util.UUID
                .randomUUID()
                .toString(),
            url,
        )!!

    private fun cover(id: Long): String? = jdbc.queryForObject("SELECT cover_url FROM games WHERE id = ?", String::class.java, id)

    private fun retry() {
        jdbc.update("UPDATE local_image_migrations SET attempted_at = NOW() - INTERVAL '11 minutes'")
    }

    @Test
    fun `disabled routine never sends or removes images`() {
        val url = file("game.png")
        val id = game(url)
        service(false).runBatch()
        assertEquals(url, cover(id))
        assertTrue(exists(url))
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM local_image_migrations", Int::class.java))
        verify(exactly = 0) { remote.upload(any(), any()) }
    }

    @Test
    fun `migrates only approved domains preserving shared links metadata primary order and bytes`() {
        val url = file("shared.png")
        val id = game(url)
        jdbc.update("INSERT INTO game_images(game_id, url, is_primary) VALUES (?, ?, TRUE)", id, url)
        jdbc.update("INSERT INTO magazine_publications(name, normalized_name) VALUES ('Revista', 'revista')")
        jdbc.update("INSERT INTO magazine_issues(publication_id, number, identity_key, cover_url) VALUES (1, '1', '1', ?)", url)
        jdbc.update("INSERT INTO artwork_artists(name, portrait_url) VALUES ('Artista', ?)", url)
        jdbc.update("INSERT INTO artworks(title, movements) VALUES ('Obra', '[\"Simbolismo\"]')")
        jdbc.update("INSERT INTO artwork_images(artwork_id, url, caption, is_primary) VALUES (1, ?, 'Legenda', TRUE)", url)
        val bookUrl = file("book.png")
        jdbc.update("INSERT INTO books(title, slug, fingerprint, cover_url) VALUES ('Livro', 'livro', 'book', ?)", bookUrl)
        service().runBatch()
        assertEquals(asset, cover(id))
        for ((table, column) in listOf(
            "game_images" to "url",
            "magazine_issues" to "cover_url",
            "artwork_artists" to "portrait_url",
            "artwork_images" to "url",
        )) {
            assertEquals(asset, jdbc.queryForObject("SELECT $column FROM $table WHERE id = 1", String::class.java))
        }
        assertEquals("Legenda", jdbc.queryForObject("SELECT caption FROM artwork_images WHERE id = 1 AND is_primary", String::class.java))
        assertTrue(jdbc.queryForObject("SELECT is_primary FROM game_images WHERE id = 1", Boolean::class.java)!!)
        assertFalse(exists(url))
        assertTrue(exists(bookUrl))
        assertEquals(bookUrl, jdbc.queryForObject("SELECT cover_url FROM books", String::class.java))
        verify {
            remote.upload(
                match { it.bytes.contentEquals(bytes) },
                match {
                    it.sourceReference.entityType == "magazine_issue" &&
                        it.tags == listOf("revista", "Revista")
                },
            )
        }
        verify { remote.upload(any(), match { it.movements == listOf("Simbolismo") && it.movementsRevision == 1L }) }
        service().runBatch()
        verify(exactly = 5) { remote.upload(any(), any()) }
    }

    @Test
    fun `upload failure keeps links and file retries later and does not starve other images`() {
        val first = file("a.png")
        val firstId = game(first)
        every { remote.upload(any(), any()) } throws IllegalStateException("offline")
        service(limit = 1).runBatch()
        assertTrue(exists(first))
        assertEquals(first, cover(firstId))
        val next = file("b.png")
        val nextId = game(next)
        every { remote.upload(any(), any()) } returns asset
        service(limit = 1).runBatch()
        assertEquals(asset, cover(nextId))
        assertEquals(first, cover(firstId))
        retry()
        service().runBatch()
        assertEquals(asset, cover(firstId))
        assertFalse(exists(first))
    }

    @Test
    fun `remote byte mismatch cannot change links or delete local file`() {
        val url = file("game.png")
        val id = game(url)
        every { remote.content(any(), null, null) } returns ResponseEntity.ok(byteArrayOf(1))
        service().runBatch()
        assertEquals(url, cover(id))
        assertTrue(exists(url))
    }

    @Test
    fun `commit failure rolls back every link and preserves original file`() {
        val url = file("game.png")
        val id = game(url)
        jdbc.execute("ALTER TABLE local_image_migrations ADD CONSTRAINT simulated_failure CHECK (remote_url IS NULL)")
        try {
            service().runBatch()
            assertEquals(url, cover(id))
            assertTrue(exists(url))
        } finally {
            jdbc.execute("ALTER TABLE local_image_migrations DROP CONSTRAINT simulated_failure")
        }
        retry()
        service().runBatch()
        assertEquals(asset, cover(id))
        assertFalse(exists(url))
    }

    @Test
    fun `concurrent cover replacement is preserved without deleting original`() {
        val url = file("game.png")
        val id = game(url)
        every { remote.upload(any(), any()) } answers {
            jdbc.update("UPDATE games SET cover_url = '/covers/new.png' WHERE id = ?", id)
            asset
        }
        service().runBatch()
        assertEquals("/covers/new.png", cover(id))
        assertTrue(exists(url))
    }

    @Test
    fun `movement correction during upload defers association until latest revision`() {
        val url = file("art.png")
        jdbc.update("INSERT INTO artworks(title) VALUES ('Obra')")
        jdbc.update("INSERT INTO artwork_images(artwork_id, url, is_primary) VALUES (1, ?, TRUE)", url)
        every { remote.upload(any(), any()) } answers {
            jdbc.update("UPDATE artworks SET movements_revision = 2 WHERE id = 1")
            asset
        }
        service().runBatch()
        assertEquals(url, jdbc.queryForObject("SELECT url FROM artwork_images", String::class.java))
        assertTrue(exists(url))
        every { remote.upload(any(), any()) } returns asset
        every { artworks.artworkMetadata(1) } returns VisualLibraryUploadMetadata.forEntity("artwork", 1).copy(movementsRevision = 2)
        retry()
        service().runBatch()
        assertEquals(asset, jdbc.queryForObject("SELECT url FROM artwork_images", String::class.java))
        assertFalse(exists(url))
    }

    @Test
    fun `cleanup survives restart after commit and verifies remote again`() {
        val url = file("game.png")
        val id = game(url)
        every { remote.content(any(), null, null) } returnsMany listOf(ResponseEntity.ok(bytes), ResponseEntity.status(503).body(null))
        service().runBatch()
        assertEquals(asset, cover(id))
        assertTrue(exists(url))
        assertEquals(1, repository.pendingCleanup(10).size)
        every { remote.content(any(), null, null) } returns ResponseEntity.ok(bytes)
        service().runBatch()
        assertFalse(exists(url))
        assertTrue(repository.pendingCleanup(10).isEmpty())
        verify(exactly = 1) { remote.upload(any(), any()) }
    }

    @Test
    fun `cleanup recognizes file already removed before state commit`() {
        val url = file("game.png")
        game(url)
        every { remote.content(any(), null, null) } returnsMany listOf(ResponseEntity.ok(bytes), ResponseEntity.status(503).body(null))
        service().runBatch()
        Files.delete(root.resolve("game.png"))
        every { remote.content(any(), null, null) } returns ResponseEntity.ok(bytes)
        service().runBatch()
        assertTrue(repository.pendingCleanup(10).isEmpty())
        verify(exactly = 1) { remote.upload(any(), any()) }
    }

    @Test
    fun `file changed during upload prevents committing references`() {
        val url = file("game.png")
        val id = game(url)
        every { remote.upload(any(), any()) } answers {
            Files.write(root.resolve("game.png"), byteArrayOf(7, 8, 9))
            asset
        }
        service().runBatch()
        assertEquals(url, cover(id))
        assertTrue(exists(url))
        assertTrue(repository.pendingCleanup(10).isEmpty())
    }

    @Test
    fun `cleanup keeps files referenced by domains outside scope and retries when released`() {
        val url = file("shared.png")
        game(url)
        jdbc.update("INSERT INTO books(title, slug, fingerprint, cover_url) VALUES ('Livro', 'livro', 'book', ?)", url)
        service().runBatch()
        assertTrue(exists(url))
        assertEquals(url, jdbc.queryForObject("SELECT cover_url FROM books", String::class.java))
        jdbc.update("UPDATE books SET cover_url = NULL")
        service().runBatch()
        assertFalse(exists(url))
    }

    @Test
    fun `cleanup cannot delete a file replaced after database commit`() {
        val url = file("game.png")
        game(url)
        every { remote.content(any(), null, null) } returnsMany listOf(ResponseEntity.ok(bytes), ResponseEntity.status(503).body(null))
        service().runBatch()
        Files.write(root.resolve("game.png"), byteArrayOf(4, 5, 6))
        every { remote.content(any(), null, null) } returns ResponseEntity.ok(bytes)
        service().runBatch()
        assertTrue(exists(url))
        assertEquals(1, repository.pendingCleanup(10).size)
    }

    @Test
    fun `remote duplicate game gallery entry merges keeping principal and original local row`() {
        val url = file("game.png")
        val id = game(url)
        jdbc.update("INSERT INTO game_images(game_id, url, is_primary) VALUES (?, ?, FALSE)", id, url)
        jdbc.update("INSERT INTO game_images(game_id, url, is_primary) VALUES (?, ?, TRUE)", id, asset)
        service().runBatch()
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM game_images", Int::class.java))
        assertEquals(asset, jdbc.queryForObject("SELECT url FROM game_images WHERE id = 1 AND is_primary", String::class.java))
        assertFalse(exists(url))
    }

    @Test
    fun `path traversal symbolic links missing and invalid files are preserved without upload`() {
        val external = Files.createTempFile("image-outside-", ".png")
        Files.write(external, bytes)
        try {
            Files.createSymbolicLink(root.resolve("link.png"), external)
            for (url in listOf("/covers/../${external.fileName}", "/covers/link.png", "/covers/missing.png", file("invalid.png"))) game(url)
            Files.write(root.resolve("invalid.png"), byteArrayOf(1, 2))
            service().runBatch()
            assertTrue(Files.exists(external))
            assertTrue(Files.isSymbolicLink(root.resolve("link.png")))
            assertTrue(Files.exists(root.resolve("invalid.png")))
            verify(exactly = 0) { remote.upload(any(), any()) }
        } finally {
            Files.deleteIfExists(external)
        }
    }

    @Test
    fun `inaccessible local directory must not be marked as cleaned`() {
        assumeTrue(System.getProperty("user.name") != "root" && Files.getFileStore(root).supportsFileAttributeView("posix"))
        val url = file("restricted/game.png")
        game(url)
        every { remote.content(any(), null, null) } returnsMany listOf(ResponseEntity.ok(bytes), ResponseEntity.status(503).body(null))
        service().runBatch()
        val directory = root.resolve("restricted")
        val permissions = Files.getPosixFilePermissions(directory)
        try {
            Files.setPosixFilePermissions(directory, PosixFilePermissions.fromString("---------"))
            every { remote.content(any(), null, null) } returns ResponseEntity.ok(bytes)
            service().runBatch()
            assertEquals(1, repository.pendingCleanup(10).size)
        } finally {
            Files.setPosixFilePermissions(directory, permissions)
        }
        assertTrue(exists(url))
        service().runBatch()
        assertFalse(exists(url))
    }

    @Test
    fun `busy database defers migration instead of waiting indefinitely`() {
        val url = file("game.png")
        val id = game(url)
        val release = Executors.newSingleThreadScheduledExecutor()
        try {
            jdbc.dataSource!!.connection.use { connection ->
                connection.autoCommit = false
                connection.createStatement().use { it.execute("LOCK TABLE games IN ROW EXCLUSIVE MODE") }
                // Release even when a regression removes the migration lock timeout.
                val fallback = release.schedule({ connection.rollback() }, 5, TimeUnit.SECONDS)
                try {
                    service().runBatch()
                    assertEquals(url, cover(id))
                    assertTrue(exists(url))
                } finally {
                    fallback.cancel(false)
                    connection.rollback()
                }
            }
        } finally {
            release.shutdownNow()
        }
        retry()
        service().runBatch()
        assertEquals(asset, cover(id))
        assertFalse(exists(url))
    }

    @Test
    fun `database advisory lock excludes another worker instance`() {
        val url = file("game.png")
        game(url)
        var competingWorkerRan = false
        repository.withWorkerLock { repository.withWorkerLock { competingWorkerRan = true } }
        assertFalse(competingWorkerRan)
        service().runBatch()
        assertFalse(exists(url))
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }
}
