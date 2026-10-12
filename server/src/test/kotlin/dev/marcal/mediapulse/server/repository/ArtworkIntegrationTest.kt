package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.artwork.ArtworkArtistRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkExperienceRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkListRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkRequest
import dev.marcal.mediapulse.server.model.artwork.ArtworkExperienceType
import dev.marcal.mediapulse.server.repository.crud.ArtworkArtistRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkExperienceRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkImageRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkListItemRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkListRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkRepository
import dev.marcal.mediapulse.server.repository.crud.MediaRatingRepository
import dev.marcal.mediapulse.server.repository.query.ArtworkQueryRepository
import dev.marcal.mediapulse.server.service.artwork.ArtworkImageService
import dev.marcal.mediapulse.server.service.artwork.ArtworkService
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import dev.marcal.mediapulse.server.util.TxUtil
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
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ArtworkIntegrationTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var service: ArtworkService
    private lateinit var gallery: ArtworkImageService
    private lateinit var tx: TxUtil
    private lateinit var jdbc: JdbcTemplate

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
                setPackagesToScan("dev.marcal.mediapulse.server.model.artwork", "dev.marcal.mediapulse.server.model.rating")
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "validate"))
                afterPropertiesSet()
            }
        val emf = factory.`object`!!
        val repositories = JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf))
        val ratingRepo = repositories.getRepository(MediaRatingRepository::class.java)
        val imageRepo = repositories.getRepository(ArtworkImageRepository::class.java)
        service =
            ArtworkService(
                repositories.getRepository(ArtworkRepository::class.java),
                repositories.getRepository(ArtworkArtistRepository::class.java),
                repositories.getRepository(ArtworkExperienceRepository::class.java),
                imageRepo,
                repositories.getRepository(ArtworkListRepository::class.java),
                repositories.getRepository(ArtworkListItemRepository::class.java),
                ArtworkQueryRepository(NamedParameterJdbcTemplate(source)),
                MediaRatingQueryRepository(ratingRepo),
                ratingRepo,
            )
        gallery = ArtworkImageService(service, imageRepo, mockk<ImageStorageService>())
        tx = TxUtil(JpaTransactionManager(emf))
        jdbc = JdbcTemplate(source)
    }

    @BeforeEach
    fun clear() {
        jdbc.execute(
            "TRUNCATE artwork_artists, artworks, artwork_experiences, artwork_images, artwork_lists, artwork_list_items, media_ratings RESTART IDENTITY CASCADE",
        )
    }

    @AfterAll
    fun close() {
        factory.destroy()
    }

    @Test
    fun `partial capture and repeated experiences persist with local artist works`() {
        val captured =
            tx.inTx {
                service.create(
                    ArtworkRequest(
                        title = "Ninfas e sátiro",
                        year = 1873,
                        type = "Pintura",
                        movement = "Academicismo francês",
                        artist = ArtworkArtistRequest(name = "William-Adolphe Bouguereau", nationality = "Francesa"),
                        experience = ArtworkExperienceRequest(sourceName = "Historia Arte", notes = "Descobri estudando arte"),
                    ),
                )
            }
        val id = captured.artwork.id
        assertNull(captured.experiences.single().experiencedAt)
        assertNull(captured.experiences.single().experienceType)
        val visit =
            tx.inTx {
                service.saveExperience(
                    id,
                    null,
                    ArtworkExperienceRequest(
                        experiencedAt = LocalDate.of(2026, 10, 12),
                        experienceType = ArtworkExperienceType.IN_PERSON,
                        sourceName = "Clark",
                        notes = "Vi o original",
                    ),
                )
            }
        tx.inTx { service.favorite(id, true) }
        tx.inTx { service.update(id, ArtworkRequest(title = "Ninfas e sátiro", artistId = captured.artist!!.id, year = 1873)) }
        val persisted = service.details(id)
        assertEquals(2, persisted.experiences.size)
        assertTrue(persisted.artwork.favorite)
        assertEquals(visit.id, persisted.experiences.first().id)
        assertEquals(listOf(id), service.library(null, captured.artist!!.id, null, null, null, null, 0, 24).items.map { it.id })
        tx.inTx { service.deleteExperience(id, visit.id) }
        assertEquals(1, service.details(id).experiences.size)
    }

    @Test
    fun `multiple lists and filters never duplicate works and deletion only removes links`() {
        val one = tx.inTx { service.create(ArtworkRequest(title = "Composição", type = "Pintura", movement = "Abstracionismo")) }.artwork
        val two = tx.inTx { service.create(ArtworkRequest(title = "Paisagem", type = "Pintura")) }.artwork
        val first = tx.inTx { service.saveList(null, ArtworkListRequest("Abstratas")) }
        val second = tx.inTx { service.saveList(null, ArtworkListRequest("Impressionistas")) }
        tx.inTx { service.addToList(one.id, first.id) }
        tx.inTx { service.addToList(one.id, first.id) }
        tx.inTx { service.addToList(one.id, second.id) }
        assertEquals(2, service.details(one.id).lists.size)
        assertEquals(1L, service.lists().first { it.id == first.id }.count)
        assertEquals(listOf(one.id), service.library(null, null, null, null, null, first.id, 0, 24).items.map { it.id })
        assertEquals(
            listOf(one.id),
            service.library("composição", null, "Pintura", "Abstracionismo", false, null, 0, 24).items.map { it.id },
        )
        val page = service.library(null, null, null, null, null, null, 0, 1)
        assertEquals(listOf(two.id), page.items.map { it.id })
        assertEquals(listOf(one.id), service.library(null, null, null, null, null, null, page.nextPage!!, 1).items.map { it.id })
        tx.inTx { service.deleteList(first.id) }
        assertEquals(listOf(second.id), service.details(one.id).lists.map { it.id })
        assertFalse(service.details(one.id).artwork.favorite)
    }

    @Test
    fun `gallery maintains one primary image and cascade deletion preserves artist and list`() {
        val captured =
            tx.inTx {
                service.create(
                    ArtworkRequest(
                        "Obra",
                        artist = ArtworkArtistRequest("Artista"),
                        experience = ArtworkExperienceRequest(notes = "Notas"),
                    ),
                )
            }
        val id = captured.artwork.id
        val list = tx.inTx { service.saveList(null, ArtworkListRequest("Lista")) }
        tx.inTx { service.addToList(id, list.id) }
        val first = tx.inTx { gallery.attach(id, "/covers/artworks/1.png", null, null) }
        val second = tx.inTx { gallery.attach(id, "/covers/artworks/2.png", "https://example.com/2.png", "Detalhe") }
        assertTrue(first.primary)
        assertFalse(second.primary)
        tx.inTx { gallery.primary(id, second.id) }
        assertEquals(
            second.id,
            service
                .details(id)
                .images
                .single { it.primary }
                .id,
        )
        tx.inTx { gallery.remove(id, second.id) }
        assertEquals(
            first.id,
            service
                .details(id)
                .images
                .single { it.primary }
                .id,
        )
        jdbc.update("INSERT INTO media_ratings(entity_type, entity_id, rating) VALUES ('ARTWORK', ?, 5)", id)
        assertEquals(5, service.details(id).rating!!.rating)
        tx.inTx { service.delete(id) }
        assertFailsWith<ResponseStatusException> { service.details(id) }
        assertEquals(captured.artist!!.id, service.artist(captured.artist!!.id).id)
        assertEquals(0L, service.lists().single().count)
        for (table in listOf("artwork_images", "artwork_experiences", "artwork_list_items", "media_ratings")) {
            assertEquals(0L, jdbc.queryForObject("SELECT COUNT(*) FROM $table", Long::class.java))
        }
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
