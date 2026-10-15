package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillPreviewRequest
import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillStatus
import dev.marcal.mediapulse.server.api.music.MusicBrainzAlbumArtistDto
import dev.marcal.mediapulse.server.config.JacksonConfig
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistCredit
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistRef
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupCandidate
import dev.marcal.mediapulse.server.integration.spotify.SpotifyApiClient
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyAlbum
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyArtist
import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import dev.marcal.mediapulse.server.repository.crud.AlbumArtistRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumCreditBackfillRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.repository.query.AlbumArtistQueryRepository
import dev.marcal.mediapulse.server.service.music.AlbumArtistCreditsService
import dev.marcal.mediapulse.server.service.music.AlbumCreditBackfillService
import dev.marcal.mediapulse.server.util.FingerprintUtil
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.spyk
import kotlinx.coroutines.runBlocking
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
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue

@Tag("integration")
@Testcontainers(disabledWithoutDocker = true)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AlbumCreditBackfillIntegrationTest {
    private lateinit var factory: LocalContainerEntityManagerFactoryBean
    private lateinit var jdbc: JdbcTemplate
    private lateinit var tx: TxUtil
    private lateinit var credits: AlbumArtistRepository
    private lateinit var query: AlbumArtistQueryRepository
    private lateinit var artists: ArtistRepository
    private lateinit var updates: AlbumArtistCreditsService
    private lateinit var batches: AlbumCreditBackfillRepository
    private lateinit var spotify: SpotifyApiClient
    private lateinit var mb: MusicBrainzApiClient
    private lateinit var service: AlbumCreditBackfillService

    @BeforeAll
    fun setup() {
        val source = DriverManagerDataSource(postgres.jdbcUrl, postgres.username, postgres.password)
        Flyway
            .configure()
            .dataSource(source)
            .load()
            .migrate()
        jdbc = JdbcTemplate(source)
        factory =
            LocalContainerEntityManagerFactoryBean().apply {
                dataSource = source
                setPackagesToScan("dev.marcal.mediapulse.server.model.music")
                jpaVendorAdapter = HibernateJpaVendorAdapter()
                setJpaPropertyMap(mapOf("hibernate.hbm2ddl.auto" to "validate"))
                afterPropertiesSet()
            }
        val emf = factory.`object`!!
        artists =
            JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(emf)).getRepository(ArtistRepository::class.java)
        tx = TxUtil(JpaTransactionManager(emf))
        credits = AlbumArtistRepository(jdbc)
        query = AlbumArtistQueryRepository(NamedParameterJdbcTemplate(source))
        updates = AlbumArtistCreditsService(credits, artists)
        batches = AlbumCreditBackfillRepository(jdbc, JacksonConfig().objectMapper())
    }

    @BeforeEach
    fun clear() {
        jdbc.execute("TRUNCATE artists, album_credit_backfill_batches RESTART IDENTITY CASCADE")
        spotify = mockk()
        mb = mockk()
        service = backfill()
    }

    @AfterAll
    fun close() = factory.destroy()

    private fun backfill(audit: AlbumCreditBackfillRepository = batches) =
        AlbumCreditBackfillService(audit, credits, query, artists, spotify, mb, updates, tx)

    @Test
    fun `preview is catalog read only and apply resume and undo preserve album history`() =
        runBlocking {
            val id = album("Pilot", "pilot")
            coEvery { spotify.getAlbum("pilot") } returns remote("Pilot", "pilot")
            val before = query.findByAlbumIds(listOf(id))[id]!!
            val revision = credits.revision(id)
            val preview = service.preview(AlbumCreditBackfillPreviewRequest(listOf(id)))
            assertEquals(AlbumCreditBackfillStatus.READY, preview.items.single().status)
            assertEquals(
                2,
                preview.items
                    .single()
                    .proposed.size,
            )
            assertEquals(before, query.findByAlbumIds(listOf(id))[id])
            assertEquals(revision, credits.revision(id))
            assertNull(artists.findBySpotifyId("pilot-guest"))
            assertTrue(service.recent().any { it.batchId == preview.batchId })
            val applied = service.apply(preview.batchId)
            assertEquals(AlbumCreditBackfillStatus.APPLIED, applied.items.single().status)
            assertEquals(
                2,
                applied.items
                    .single()
                    .after!!
                    .size,
            )
            val afterRevision = credits.revision(id)
            assertEquals(applied, backfill().apply(preview.batchId))
            assertEquals(afterRevision, credits.revision(id))
            coVerify(exactly = 1) { spotify.getAlbum("pilot") }
            val undone = backfill().undo(preview.batchId)
            assertEquals(AlbumCreditBackfillStatus.UNDONE, undone.items.single().status)
            assertEquals(before, query.findByAlbumIds(listOf(id))[id])
            val undoRevision = credits.revision(id)
            service.undo(preview.batchId)
            assertEquals(undoRevision, credits.revision(id))
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM albums WHERE id=?", Long::class.java, id))
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM track_playbacks WHERE album_id=?", Long::class.java, id))
            assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM artists WHERE spotify_id='pilot-guest'", Long::class.java))
        }

    @Test
    fun `manual confirmation after batch protects undo even when credits do not change`() =
        runBlocking {
            val id = album("Confirmed", groupId = "known-group")
            val incoming =
                listOf(
                    MbArtistCredit("Confirmed artist", MbArtistRef("mb-main", "Confirmed artist"), " & "),
                    MbArtistCredit("Confirmed guest", MbArtistRef("mb-guest", "Confirmed guest")),
                )
            coEvery { mb.getReleaseGroup("known-group") } returns
                MbReleaseGroupCandidate("known-group", "Confirmed", artistCredit = incoming)
            val preview = service.preview(AlbumCreditBackfillPreviewRequest(listOf(id)))
            assertEquals(AlbumArtistSource.MUSICBRAINZ_CONFIRMED, preview.items.single().provider)
            val applied = service.apply(preview.batchId)
            assertEquals(AlbumCreditBackfillStatus.APPLIED, applied.items.single().status)
            val after = query.findByAlbumIds(listOf(id))[id]!!
            tx.inTx {
                updates.applyMusicBrainz(
                    id,
                    incoming.mapIndexed { index, credit ->
                        MusicBrainzAlbumArtistDto(credit.artist!!.id, credit.artist!!.name, credit.name!!, credit.joinPhrase, index)
                    },
                )
            }
            assertEquals(after, query.findByAlbumIds(listOf(id))[id])
            assertEquals(
                AlbumCreditBackfillStatus.UNDO_CONFLICT,
                service
                    .undo(preview.batchId)
                    .items
                    .single()
                    .status,
            )
            assertEquals(after, query.findByAlbumIds(listOf(id))[id])
        }

    @Test
    fun `stale preview does not overwrite a later automatic update`() =
        runBlocking {
            val id = album("Stale", "stale")
            val response = remote("Stale", "stale")
            coEvery { spotify.getAlbum("stale") } returns response
            val preview = service.preview(AlbumCreditBackfillPreviewRequest(listOf(id)))
            tx.inTx { updates.applySpotify(id, response.artists) }
            val after = query.findByAlbumIds(listOf(id))[id]
            assertEquals(
                AlbumCreditBackfillStatus.CONFLICT,
                service
                    .apply(preview.batchId)
                    .items
                    .single()
                    .status,
            )
            assertEquals(after, query.findByAlbumIds(listOf(id))[id])
        }

    @Test
    fun `failed audit rolls back credits and artists and same batch can resume`() =
        runBlocking {
            val id = album("Retry", "retry")
            coEvery { spotify.getAlbum("retry") } returns remote("Retry", "retry")
            val audit = spyk(batches)
            val runner = backfill(audit)
            val preview = runner.preview(AlbumCreditBackfillPreviewRequest(listOf(id)))
            val before = query.findByAlbumIds(listOf(id))[id]
            every { audit.save(any(), match { it.status == AlbumCreditBackfillStatus.APPLIED }) } throws
                IllegalStateException("test write failure")
            assertEquals(
                AlbumCreditBackfillStatus.ERROR,
                runner
                    .apply(preview.batchId)
                    .items
                    .single()
                    .status,
            )
            assertEquals(before, query.findByAlbumIds(listOf(id))[id])
            assertNull(artists.findBySpotifyId("retry-guest"))
            every { audit.save(any(), match { it.status == AlbumCreditBackfillStatus.APPLIED }) } answers { callOriginal() }
            assertEquals(
                AlbumCreditBackfillStatus.APPLIED,
                runner
                    .apply(preview.batchId)
                    .items
                    .single()
                    .status,
            )
            assertEquals(2, query.findByAlbumIds(listOf(id))[id]!!.size)
            coVerify(exactly = 1) { spotify.getAlbum("retry") }
        }

    @Test
    fun `provider errors are per album and completed credits are skipped`() =
        runBlocking {
            val good = album("Good", "good")
            val failed = album("Failed", "failed")
            coEvery { spotify.getAlbum("good") } returns remote("Good", "good")
            coEvery { spotify.getAlbum("failed") } throws IllegalStateException("provider unavailable")
            val preview = service.preview(AlbumCreditBackfillPreviewRequest(listOf(good, failed)))
            assertEquals(AlbumCreditBackfillStatus.ERROR, preview.items.single { it.albumId == failed }.status)
            val result = service.apply(preview.batchId)
            assertEquals(AlbumCreditBackfillStatus.APPLIED, result.items.single { it.albumId == good }.status)
            assertEquals(AlbumCreditBackfillStatus.ERROR, result.items.single { it.albumId == failed }.status)
            assertEquals(
                AlbumCreditBackfillStatus.SKIPPED,
                service
                    .preview(AlbumCreditBackfillPreviewRequest(listOf(good)))
                    .items
                    .single()
                    .status,
            )
            assertEquals(
                failed,
                service
                    .preview(AlbumCreditBackfillPreviewRequest(limit = 1))
                    .items
                    .single()
                    .albumId,
            )
            coVerify(exactly = 1) { spotify.getAlbum("good") }
            assertFailsWith<ResponseStatusException> { service.preview(AlbumCreditBackfillPreviewRequest(limit = 11)) }
        }

    @Test
    fun `identity conflicts are visible in preview without catalog writes`() =
        runBlocking {
            val id = album("Conflicting", "conflicting")
            jdbc.update("UPDATE artists SET spotify_id='other-identity' WHERE id=(SELECT artist_id FROM albums WHERE id=?)", id)
            coEvery { spotify.getAlbum("conflicting") } returns remote("Conflicting", "conflicting")
            val before = query.findByAlbumIds(listOf(id))[id]
            val preview = service.preview(AlbumCreditBackfillPreviewRequest(listOf(id)))
            assertEquals(AlbumCreditBackfillStatus.CONFLICT, preview.items.single().status)
            service.apply(preview.batchId)
            assertEquals(before, query.findByAlbumIds(listOf(id))[id])
            assertNull(artists.findBySpotifyId("conflicting-guest"))
        }

    private fun remote(
        title: String,
        id: String,
    ) = SpotifyAlbum(
        id,
        title,
        null,
        null,
        listOf(SpotifyArtist("$id-main", "$title artist"), SpotifyArtist("$id-guest", "$title guest")),
    )

    private fun album(
        title: String,
        spotifyId: String? = null,
        groupId: String? = null,
    ): Long {
        val artist =
            jdbc.queryForObject(
                "INSERT INTO artists(name,fingerprint) VALUES (?,?) RETURNING id",
                Long::class.java,
                "$title artist",
                FingerprintUtil.artistFp("$title artist"),
            )!!
        val id =
            jdbc.queryForObject(
                "INSERT INTO albums(artist_id,title,title_key,fingerprint,musicbrainz_release_group_id) VALUES (?,?,?,?,?) RETURNING id",
                Long::class.java,
                artist,
                title,
                title.lowercase(),
                "album-$title",
                groupId,
            )!!
        if (spotifyId != null) jdbc.update("INSERT INTO album_spotify_ids(album_id,spotify_id) VALUES (?,?)", id, spotifyId)
        tx.inTx { credits.initializeLegacy(id) }
        val track =
            jdbc.queryForObject(
                "INSERT INTO tracks(artist_id,title,fingerprint) VALUES (?,'Historical track',?) RETURNING id",
                Long::class.java,
                artist,
                "track-$title",
            )!!
        jdbc.update("INSERT INTO album_tracks(album_id,track_id) VALUES (?,?)", id, track)
        jdbc.update("INSERT INTO track_playbacks(track_id,album_id,source,played_at) VALUES (?,?,'SPOTIFY',NOW())", track, id)
        return id
    }

    companion object {
        @Container @JvmStatic
        val postgres = PostgreSQLContainer<Nothing>("postgres:16-alpine")
    }
}
