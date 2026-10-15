package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.music.MusicBrainzAlbumArtistDto
import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistCredit
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbArtistRef
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupCandidate
import dev.marcal.mediapulse.server.integration.plex.PlexApiClient
import dev.marcal.mediapulse.server.integration.plex.dto.PlexAlbum
import dev.marcal.mediapulse.server.integration.plex.dto.PlexArtist
import dev.marcal.mediapulse.server.integration.plex.dto.PlexGuid
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyAlbum
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyArtist
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyExtendedHistoryItem
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyRecentlyPlayedItem
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyTrack
import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import dev.marcal.mediapulse.server.repository.crud.AlbumArtistRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumMusicBrainzReleaseIdRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumSpotifyIdRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumTrackCrudRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.repository.crud.TrackMusicBrainzRecordingIdRepository
import dev.marcal.mediapulse.server.repository.crud.TrackPlaybackCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TrackRepository
import dev.marcal.mediapulse.server.repository.crud.TrackSpotifyIdRepository
import dev.marcal.mediapulse.server.service.canonical.CanonicalizationService
import dev.marcal.mediapulse.server.service.music.AlbumArtistCreditsService
import dev.marcal.mediapulse.server.service.musicbrainz.MusicBrainzPageEnrichmentService
import dev.marcal.mediapulse.server.service.plex.PlexMusicPlaybackService
import dev.marcal.mediapulse.server.service.plex.import.PlexMusicImportService
import dev.marcal.mediapulse.server.service.spotify.SpotifyExtendedPlaybackService
import dev.marcal.mediapulse.server.service.spotify.SpotifyPlaybackService
import dev.marcal.mediapulse.server.util.FingerprintUtil
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.coEvery
import io.mockk.mockk
import jakarta.persistence.EntityManager
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
import java.time.Instant
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
    private lateinit var canonical: CanonicalizationService
    private lateinit var spotify: SpotifyPlaybackService
    private lateinit var playbacks: TrackPlaybackCrudRepository
    private lateinit var entityManager: EntityManager
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
        entityManager = em
        val repositories = JpaRepositoryFactory(em)
        artists = repositories.getRepository(ArtistRepository::class.java)
        albums = repositories.getRepository(AlbumRepository::class.java)
        tx = TxUtil(JpaTransactionManager(emf))
        credits = AlbumArtistRepository(jdbc)
        service = AlbumArtistCreditsService(credits, artists)
        canonical =
            CanonicalizationService(
                credits,
                artists,
                albums,
                repositories.getRepository(TrackRepository::class.java),
                repositories.getRepository(AlbumTrackCrudRepository::class.java),
                repositories.getRepository(AlbumSpotifyIdRepository::class.java),
                repositories.getRepository(AlbumMusicBrainzReleaseIdRepository::class.java),
                repositories.getRepository(TrackSpotifyIdRepository::class.java),
                repositories.getRepository(TrackMusicBrainzRecordingIdRepository::class.java),
            )
        playbacks = repositories.getRepository(TrackPlaybackCrudRepository::class.java)
        spotify =
            SpotifyPlaybackService(
                canonical,
                playbacks,
                mockk(relaxed = true),
                service,
            )

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

    @Test
    fun `Spotify replay and Plex import retain a single album and all Spotify credits`() {
        val anchor = tx.inTx { canonical.ensureArtist("Spotify main", spotifyId = "sp-main") }
        val album =
            tx.inTx {
                canonical.ensureAlbum(anchor, "Shared album", 2025, null, spotifyId = "sp-album", musicbrainzReleaseGroupId = "shared-rg")
            }
        val incoming = listOf(SpotifyArtist("sp-main", "Spotify main"), SpotifyArtist("sp-guest", "Spotify guest"))
        val item =
            SpotifyRecentlyPlayedItem(
                "2025-01-01T00:00:00Z",
                SpotifyTrack(
                    "sp-track",
                    "Shared track",
                    120000,
                    1,
                    1,
                    listOf(incoming.first()),
                    SpotifyAlbum("sp-album", "Shared album", "2025", null, incoming),
                ),
            )
        repeat(2) { tx.inTx { runBlocking { spotify.processRecentlyPlayedItem(item, null) } } }
        val stored = query.getAlbumArtists(album.id)
        assertEquals(listOf("Spotify main", "Spotify guest"), stored.map { it.creditedName })
        assertEquals(listOf(", ", ""), stored.map { it.joinPhrase })
        assertEquals(setOf(AlbumArtistSource.SPOTIFY), stored.map { it.source }.toSet())
        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM track_playbacks WHERE album_id = ?", Long::class.java, album.id))
        val plex = mockk<PlexApiClient>()
        val mb = mockk<MusicBrainzApiClient>()
        coEvery { plex.listArtistsPaged("music", 0, 200) } returns (listOf(PlexArtist("plex-artist", "Plex partial artist")) to 1)
        coEvery { plex.listAlbumsByArtistPaged("music", "plex-artist", 0, 200) } returns (
            listOf(
                PlexAlbum(ratingKey = "plex-album", title = "Shared album", year = 2025, guids = listOf(PlexGuid("mbid://shared-release"))),
            ) to
                1
        )
        coEvery { mb.resolveReleaseGroupFromRelease("shared-release") } returns "shared-rg"
        coEvery { plex.listTracksByAlbumPaged("music", "plex-album", 0, 200) } returns
            (emptyList<dev.marcal.mediapulse.server.integration.plex.dto.PlexTrack>() to 0)
        val importer = PlexMusicImportService(plex, canonical, mockk(relaxed = true), mockk(relaxed = true), mb)
        tx.inTx { runBlocking { importer.importAllMusicLibrary("music") } }
        assertEquals(stored, query.getAlbumArtists(album.id))
        assertEquals(album.id, canonical.findAlbumByMusicBrainzReleaseId("shared-release")!!.id)
        val webhook = PlexMusicPlaybackService(canonical, playbacks, mockk(relaxed = true), mockk(relaxed = true))
        val payload =
            PlexWebhookPayload(
                "media.scrobble",
                PlexWebhookPayload.PlexMetadata(
                    type = "track",
                    title = "Shared track",
                    grandparentTitle = "Spotify main",
                    parentTitle = "Shared album",
                    parentYear = 2025,
                    parentIndex = 1,
                    index = 1,
                    lastViewedAt = Instant.parse("2025-01-01T00:03:00Z"),
                ),
            )
        repeat(2) { tx.inTx { runBlocking { webhook.processScrobble(payload) } } }
        assertEquals(stored, query.getAlbumArtists(album.id))
        assertEquals(
            1L,
            jdbc.queryForObject("SELECT COUNT(*) FROM track_playbacks WHERE album_id = ? AND source = 'PLEX'", Long::class.java, album.id),
        )
        val extended = SpotifyExtendedPlaybackService(canonical, playbacks, tx, entityManager)
        extended.processChunk(
            listOf(
                SpotifyExtendedHistoryItem(
                    ts = "2025-01-01T00:05:00Z",
                    trackName = "Shared track",
                    artistName = "Spotify main",
                    albumName = "Shared album",
                    spotifyTrackUri = "spotify:track:sp-track",
                ),
            ),
            null,
        )
        assertEquals(stored, query.getAlbumArtists(album.id))

        assertEquals(1L, jdbc.queryForObject("SELECT COUNT(*) FROM albums WHERE title = 'Shared album'", Long::class.java))
    }

    @Test
    fun `Spotify partial lists preserve credits and confirmed MusicBrainz wins`() {
        val artistId = insertArtist("Partial main")
        val id = insertAlbum(artistId, "Partial album")
        val full = listOf(SpotifyArtist("partial-main", "Partial main"), SpotifyArtist("partial-guest", "Partial guest"))
        tx.inTx { service.applySpotify(id, full) }
        val stored = query.getAlbumArtists(id)
        for (partial in listOf(null, emptyList(), full.take(1), listOf(SpotifyArtist("missing", null)))) {
            tx.inTx { service.applySpotify(id, partial) }
            assertEquals(stored, query.getAlbumArtists(id))
        }
        tx.inTx { service.applySpotify(id, full.reversed()) }
        assertEquals(stored.map { it.artistId }.reversed(), query.getAlbumArtists(id).map { it.artistId })
        tx.inTx {
            service.applyMusicBrainz(id, listOf(MusicBrainzAlbumArtistDto("confirmed-main", "Partial main", "Confirmed alias", "", 0)))
        }
        val confirmed = query.getAlbumArtists(id)
        tx.inTx { service.applySpotify(id, full + SpotifyArtist("ignored-artist", "Ignored artist")) }
        assertEquals(confirmed, query.getAlbumArtists(id))
        assertNull(artists.findBySpotifyId("ignored-artist"))
    }

    @Test
    fun `Spotify ambiguous identities preserve legacy credits without partial writes`() {
        val anchor = insertArtist("Conflict Spotify main", "conflict-spotify-mbid")
        jdbc.update("UPDATE artists SET spotify_id = 'original-spotify-id' WHERE id = ?", anchor)
        val id = insertAlbum(anchor, "Conflicting Spotify album")
        tx.inTx { credits.initializeLegacy(id) }
        val before = query.getAlbumArtists(id)
        tx.inTx {
            service.applySpotify(
                id,
                listOf(SpotifyArtist("would-be-new", "Would be new"), SpotifyArtist("wrong-id", "Conflict Spotify main")),
            )
        }
        assertEquals(before, query.getAlbumArtists(id))
        assertNull(artists.findBySpotifyId("would-be-new"))
        assertEquals("original-spotify-id", artists.findById(anchor).orElseThrow().spotifyId)
    }

    @Test
    fun `album merge also protects Spotify credits on source albums`() {
        val source = insertAlbum(legacyArtist, "Spotify source")
        val target = insertAlbum(legacyArtist, "Spotify target")
        tx.inTx { service.applySpotify(source, listOf(SpotifyArtist("protected-spotify", "Protected Spotify artist"))) }
        val merger =
            AlbumMergeRepository(
                org.springframework.jdbc.core.namedparam
                    .NamedParameterJdbcTemplate(jdbc.dataSource!!),
            )
        assertFailsWith<ResponseStatusException> {
            tx.inTx { merger.merge(target, listOf(source), legacyArtist, null, emptyList(), 0) }
        }
        assertEquals(AlbumArtistSource.SPOTIFY, credits.findByAlbumId(source).single().source)
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
