package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.music.MusicBrainzAlbumArtistDto
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistCredit
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistRef
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupCandidate
import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import dev.marcal.mediapulse.server.repository.crud.AlbumArtistRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumMusicBrainzReleaseIdRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.service.music.AlbumArtistCreditsService
import dev.marcal.mediapulse.server.service.musicbrainz.MusicBrainzPageEnrichmentService
import dev.marcal.mediapulse.server.util.FingerprintUtil
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.coroutines.runBlocking
import org.flywaydb.core.Flyway
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory
import org.springframework.jdbc.core.JdbcTemplate
import org.springframework.jdbc.datasource.DriverManagerDataSource
import org.springframework.orm.jpa.JpaTransactionManager
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean
import org.springframework.orm.jpa.SharedEntityManagerCreator
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter
import org.springframework.web.server.ResponseStatusException
import org.testcontainers.containers.PostgreSQLContainer
import org.testcontainers.junit.jupiter.Container
import org.testcontainers.junit.jupiter.Testcontainers
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlbumArtistCreditsIntegrationTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var jdbc: JdbcTemplate
    private lateinit var tx: TxUtil
    private lateinit var credits: AlbumArtistRepository
    private lateinit var artists: ArtistRepository
    private lateinit var albums: AlbumRepository
    private lateinit var query: MusicQueryRepository
    private lateinit var service: AlbumArtistCreditsService
    private var legacyArtist = 0L
    private var legacyAlbum = 0L

    @BeforeAll
    fun setup() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .target("67")
            .load()
            .migrate()
        jdbc = JdbcTemplate(source)
        legacyArtist = insertArtist("Legacy Artist")
        legacyAlbum = insertAlbum(legacyArtist, "Legacy Album")
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                setPackagesToScan("dev.marcal.mediapulse.server.model.music")
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "validate"))
                afterPropertiesSet()
            }
        val emf = factory.`object`!!
        val em = SharedEntityManagerCreator.createSharedEntityManager(emf)
        val repositories = JpaRepositoryFactory(em)
        artists = repositories.getRepository(ArtistRepository::class.java)
        albums = repositories.getRepository(AlbumRepository::class.java)
        tx = TxUtil(JpaTransactionManager(emf))
        credits = AlbumArtistRepository(jdbc)
        service = AlbumArtistCreditsService(credits, artists)
        query = MusicQueryRepository(em, mockk(relaxed = true), mockk(relaxed = true), albums, mockk(relaxed = true))
    }

    @AfterAll
    fun close() {
        factory.destroy()
    }

    @Test
    fun `migration fills existing credits without changing legacy identities`() {
        val credit = credits.findByAlbumId(legacyAlbum).single()
        assertEquals(legacyArtist, credit.artistId)
        assertEquals(0, credit.position)
        assertEquals("Legacy Artist", credit.creditedName)
        assertEquals(AlbumArtistSource.LEGACY, credit.source)
        assertEquals(legacyArtist, albums.findById(legacyAlbum).orElseThrow().artistId)
        assertEquals("legacy-Legacy Album", albums.findById(legacyAlbum).orElseThrow().fingerprint)
    }

    @Test
    fun `confirmed credits reuse identities preserve order and are idempotent`() {
        val artistId = insertArtist("First Artist")
        val albumId = insertAlbum(artistId, "Collaborative Album")
        tx.inTx { credits.initializeLegacy(albumId) }
        val incoming =
            listOf(
                MusicBrainzAlbumArtistDto("mb-first", "First Artist", "First alias", " & ", 0),
                MusicBrainzAlbumArtistDto("mb-second", "Second Artist", "Second Artist", "", 1),
            )
        tx.inTx { service.applyMusicBrainz(albumId, incoming) }
        val first = query.getAlbumArtists(albumId)
        tx.inTx { service.applyMusicBrainz(albumId, incoming) }
        tx.inTx { credits.initializeLegacy(albumId) }
        assertEquals(first, query.getAlbumArtists(albumId))
        val page = query.getAlbumPage(albumId)
        assertEquals(first, page.artists)
        assertEquals(artistId, page.artistId)
        assertEquals("First Artist", page.artistName)
        assertEquals(0L, page.totalPlays)
        assertEquals(listOf("First alias", "Second Artist"), first.map { it.creditedName })
        assertEquals(" & ", first.first().joinPhrase)
        assertEquals(artistId, first.first().artistId)
        assertEquals("mb-first", artists.findById(artistId).orElseThrow().musicbrainzArtistId)
        assertEquals(
            2L,
            jdbc.queryForObject("SELECT COUNT(*) FROM artists WHERE musicbrainz_artist_id IN ('mb-first', 'mb-second')", Long::class.java),
        )
        assertEquals(artistId, albums.findById(albumId).orElseThrow().artistId)
        tx.inTx { service.applyMusicBrainz(albumId, emptyList()) }
        assertEquals(first, query.getAlbumArtists(albumId))
    }

    @Test
    fun `enrichment rolls back release group artist creation and credits on identity conflict`() =
        runBlocking {
            val anchor = insertArtist("Unrelated legacy artist")
            val albumId = insertAlbum(anchor, "Conflict Album")
            tx.inTx { credits.initializeLegacy(albumId) }
            val conflicting = insertArtist("Conflicting artist", "existing-mbid")
            val client = mockk<MusicBrainzApiClient>()
            coEvery { client.getReleaseGroup("conflict-rg") } returns
                MbReleaseGroupCandidate(
                    id = "conflict-rg",
                    title = "Conflict Album",
                    artistCredit =
                        listOf(
                            MbArtistCredit("New artist", MbArtistRef("new-mbid", "New artist"), " & "),
                            MbArtistCredit("Conflicting artist", MbArtistRef("different-mbid", "Conflicting artist")),
                        ),
                )
            val enrichment =
                MusicBrainzPageEnrichmentService(
                    client,
                    albums,
                    artists,
                    mockk<AlbumMusicBrainzReleaseIdRepository>(),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    mockk(relaxed = true),
                    service,
                    credits,
                    tx,
                )
            assertFailsWith<ResponseStatusException> { enrichment.applyAlbum(albumId, "conflict-rg") }
            assertNull(albums.findById(albumId).orElseThrow().musicbrainzReleaseGroupId)
            assertNull(artists.findByMusicbrainzArtistId("new-mbid"))
            assertNull(artists.findById(anchor).orElseThrow().musicbrainzArtistId)
            assertEquals("existing-mbid", artists.findById(conflicting).orElseThrow().musicbrainzArtistId)
            assertEquals(anchor, credits.findByAlbumId(albumId).single().artistId)
        }

    @Test
    fun `existing MusicBrainz identity wins without relinking an unrelated legacy artist`() {
        val anchor = insertArtist("Obsolete album artist")
        val known = insertArtist("Canonical person", "known-mbid")
        val album = insertAlbum(anchor, "Previously misattributed album")
        tx.inTx { credits.initializeLegacy(album) }
        tx.inTx {
            service.applyMusicBrainz(
                album,
                listOf(MusicBrainzAlbumArtistDto("known-mbid", "Provider name", "Credit alias", "", 0)),
            )
        }
        assertEquals(known, credits.findByAlbumId(album).single().artistId)
        assertEquals(anchor, albums.findById(album).orElseThrow().artistId)
        assertNull(artists.findById(anchor).orElseThrow().musicbrainzArtistId)
        assertEquals("Canonical person", query.getAlbumArtists(album).single().artistName)
    }

    @Test
    fun `missing stored credits still expose legacy artist for compatibility`() {
        val id = insertAlbum(legacyArtist, "Album after rollback")
        assertEquals(legacyArtist, query.getAlbumArtists(id).single().artistId)
        tx.inTx { credits.initializeLegacy(id) }
        assertEquals(AlbumArtistSource.LEGACY, query.getAlbumArtists(id).single().source)
    }

    @Test
    fun `album merge rejects deleting a source with confirmed credits`() {
        val source = insertAlbum(legacyArtist, "Confirmed source")
        val target = insertAlbum(legacyArtist, "Legacy target")
        tx.inTx {
            service.applyMusicBrainz(source, listOf(MusicBrainzAlbumArtistDto("legacy-mbid", "Legacy Artist", "Legacy Artist", "", 0)))
        }
        val merger =
            AlbumMergeRepository(
                org.springframework.jdbc.core.namedparam
                    .NamedParameterJdbcTemplate(jdbc.dataSource!!),
            )
        assertFailsWith<ResponseStatusException> {
            tx.inTx { merger.merge(target, listOf(source), legacyArtist, null, emptyList(), 0) }
        }
        assertEquals(1, credits.findByAlbumId(source).size)
        assertEquals(true, albums.existsById(source))
    }

    private fun insertArtist(
        name: String,
        mbid: String? = null,
    ): Long =
        jdbc.queryForObject(
            "INSERT INTO artists(name, fingerprint, musicbrainz_artist_id) VALUES (?, ?, ?) RETURNING id",
            Long::class.java,
            name,
            FingerprintUtil.artistFp(name),
            mbid,
        )!!

    private fun insertAlbum(
        artistId: Long,
        title: String,
    ): Long =
        jdbc.queryForObject(
            "INSERT INTO albums(artist_id, title, title_key, fingerprint) VALUES (?, ?, ?, ?) RETURNING id",
            Long::class.java,
            artistId,
            title,
            title.lowercase(),
            "legacy-$title",
        )!!

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
