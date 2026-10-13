package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryUploadException
import dev.marcal.mediapulse.server.model.book.Book
import dev.marcal.mediapulse.server.model.game.Game
import dev.marcal.mediapulse.server.repository.crud.BookRepository
import dev.marcal.mediapulse.server.repository.crud.GameImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import dev.marcal.mediapulse.server.repository.hardcover.HardcoverNativeRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import dev.marcal.mediapulse.server.service.image.LibraryCoverService
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.mock.web.MockMultipartFile
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.imageio.ImageIO
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LibraryCoversIntegrationTest {
    private val root: Path = Files.createTempDirectory("media-pulse-cover-test-")
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var books: BookRepository
    private lateinit var games: GameRepository
    private lateinit var service: LibraryCoverService
    private lateinit var native: HardcoverNativeRepository
    private lateinit var tx: TxUtil
    private lateinit var jdbc: JdbcTemplate
    private val remote = mockk<VisualLibraryClient>(relaxed = true)
    private val asset = "/api/image-assets/00000000-0000-0000-0000-000000000001/content"

    @BeforeAll
    fun setup() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                setPackagesToScan("dev.marcal.mediapulse.server.model.book", "dev.marcal.mediapulse.server.model.game")
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "validate"))
                afterPropertiesSet()
            }
        val emf = factory.`object`!!
        val em = SharedEntityManagerCreator.createSharedEntityManager(emf)
        val repositories = JpaRepositoryFactory(em)
        books = repositories.getRepository(BookRepository::class.java)
        games = repositories.getRepository(GameRepository::class.java)
        val images = repositories.getRepository(GameImageCrudRepository::class.java)
        tx = TxUtil(JpaTransactionManager(emf))
        service =
            LibraryCoverService(
                books,
                games,
                images,
                LibraryImageStorageService(ImageStorageService(root.toString()), remote, WebClient.builder()),
                tx,
            )
        native =
            HardcoverNativeRepository().apply {
                javaClass.getDeclaredField("em").apply { isAccessible = true }.set(this, em)
            }
        jdbc = JdbcTemplate(source)
    }

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE books, games RESTART IDENTITY CASCADE")
        every { remote.enabled } returns true
        every { remote.upload(any(), any()) } returns asset
    }

    @AfterAll fun close() {
        factory.destroy()
        Files.walk(root).use { paths -> paths.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists) }
    }

    private fun book() = tx.inTx { books.save(Book(title = "Livro", slug = "livro", fingerprint = "book", coverUrl = "/covers/old.png")) }

    private fun game() =
        tx.inTx {
            games.save(Game(title = "Jogo", originalTitle = "Jogo", slug = "jogo", fingerprint = "game", coverUrl = "/covers/old.png"))
        }

    private fun photo() =
        MockMultipartFile(
            "cover",
            "cover.png",
            "image/png",
            ByteArrayOutputStream().use {
                ImageIO.write(BufferedImage(2, 2, BufferedImage.TYPE_INT_RGB), "png", it)
                it.toByteArray()
            },
        )

    @Test
    fun `manual remote covers persist and game primary stays unique after repeated uploads`() {
        val book = book()
        val game = game()
        jdbc.update("INSERT INTO game_images(game_id, url, kind, is_primary) VALUES (?, '/covers/old.png', 'GRID', true)", game.id)
        assertEquals(asset, service.book(book.id, photo()))
        repeat(2) { assertEquals(asset, service.game(game.id, photo())) }
        assertEquals(asset, books.findById(book.id).get().coverUrl)
        assertEquals(asset, games.findById(game.id).get().coverUrl)
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM game_images WHERE game_id = ? AND is_primary", Int::class.java, game.id))
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM game_images WHERE game_id = ?", Int::class.java, game.id))
        // An import that downloaded before a concurrent manual edit cannot replace its cover.
        tx.inTx { native.updateBookCover(book.id, "/covers/provider.png") }
        assertEquals(asset, books.findById(book.id).get().coverUrl)
        assertEquals(asset, native.storedBookCover(book.id))
    }

    @Test
    fun `failed remote replacements preserve old cover and primary and retry creates no duplicate records`() {
        val book = book()
        val game = game()
        jdbc.update("INSERT INTO game_images(game_id, url, kind, is_primary) VALUES (?, '/covers/old.png', 'GRID', true)", game.id)
        every { remote.upload(any(), any()) } throws VisualLibraryUploadException(IllegalStateException("offline"))
        assertFailsWith<VisualLibraryUploadException> { service.book(book.id, photo()) }
        assertFailsWith<VisualLibraryUploadException> { service.game(game.id, photo()) }
        assertEquals("/covers/old.png", books.findById(book.id).get().coverUrl)
        assertEquals("/covers/old.png", games.findById(game.id).get().coverUrl)
        assertEquals(
            "/covers/old.png",
            jdbc.queryForObject("SELECT url FROM game_images WHERE game_id = ? AND is_primary", String::class.java, game.id),
        )
        every { remote.upload(any(), any()) } returns asset
        service.book(book.id, photo())
        service.game(game.id, photo())
        assertEquals(1L, books.count())
        assertEquals(1L, games.count())
    }

    @Test
    fun `local uploads use distinct files and invalid uploads cannot replace saved covers`() {
        every { remote.enabled } returns false
        val book = book()
        val game = game()
        val first = service.book(book.id, photo())
        val second = service.book(book.id, photo())
        assertTrue(first != second)
        val gameUrl = service.game(game.id, photo())
        for (url in listOf(first, second, gameUrl)) assertTrue(Files.exists(root.resolve(url.removePrefix("/covers/"))))
        val fake = MockMultipartFile("cover", "not an image".toByteArray())
        assertFailsWith<ResponseStatusException> { service.book(book.id, fake) }
        assertFailsWith<ResponseStatusException> { service.game(game.id, fake) }
        assertEquals(second, books.findById(book.id).get().coverUrl)
        assertEquals(gameUrl, games.findById(game.id).get().coverUrl)
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer("postgres:16-alpine")
    }
}
