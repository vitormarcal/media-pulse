package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvEpisodeWatchSource
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeWatchCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowTitleCrudRepository
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlexEpisodeWatchServiceTest {
    private lateinit var tvShowRepository: TvShowRepository
    private lateinit var tvShowTitleCrudRepository: TvShowTitleCrudRepository
    private lateinit var tvEpisodeRepository: TvEpisodeRepository
    private lateinit var tvEpisodeWatchCrudRepository: TvEpisodeWatchCrudRepository
    private lateinit var service: PlexEpisodeWatchService

    @BeforeEach
    fun setUp() {
        tvShowRepository = mockk(relaxed = true)
        tvShowTitleCrudRepository = mockk(relaxed = true)
        tvEpisodeRepository = mockk(relaxed = true)
        tvEpisodeWatchCrudRepository = mockk(relaxed = true)
        every { tvEpisodeRepository.findByTmdbId(any()) } returns null
        every { tvEpisodeRepository.findByTvdbId(any()) } returns null
        every { tvEpisodeRepository.findByImdbId(any()) } returns null

        service =
            PlexEpisodeWatchService(
                tvShowRepository = tvShowRepository,
                tvShowTitleCrudRepository = tvShowTitleCrudRepository,
                tvEpisodeRepository = tvEpisodeRepository,
                tvEpisodeWatchCrudRepository = tvEpisodeWatchCrudRepository,
            )
    }

    @Test
    fun `deve processar scrobble de episodio e gravar show episodio e watch`() =
        runBlocking {
            val payload = episodePayload()
            val savedShow = slot<TvShow>()
            val savedEpisode = slot<TvEpisode>()
            val persistedShow =
                TvShow(
                    id = 2283,
                    originalTitle = "The Big Bang Theory",
                    year = 2009,
                    slug = "the-big-bang-theory",
                    fingerprint = "show-fp",
                )
            val persistedEpisode =
                TvEpisode(
                    id = 3963,
                    showId = 2283,
                    title = "A Expedicao Monopolar",
                    seasonNumber = 2,
                    episodeNumber = 23,
                    summary = "desc",
                    durationMs = 1260000,
                    originallyAvailableAt = LocalDate.parse("2009-05-11"),
                    fingerprint = "episode-fp",
                )

            every { tvShowRepository.findByFingerprint(any()) } returns null
            every { tvShowRepository.save(capture(savedShow)) } returns persistedShow
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { tvEpisodeRepository.findByFingerprint(any()) } returns null
            every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(any(), any(), any()) } returns null
            every { tvEpisodeRepository.save(capture(savedEpisode)) } returns persistedEpisode
            every { tvEpisodeWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs

            val result = service.processScrobble(payload)

            assertNotNull(result)
            assertEquals(3963, result.episodeId)
            assertEquals(TvEpisodeWatchSource.PLEX, result.source)
            assertEquals(Instant.ofEpochSecond(1775146349), result.watchedAt)
            assertEquals("Big Bang: A Teoria", savedShow.captured.originalTitle)
            assertEquals(2009, savedShow.captured.year)
            assertEquals("the-big-bang-theory", savedShow.captured.slug)
            assertEquals(2, savedEpisode.captured.seasonNumber)
            assertEquals(23, savedEpisode.captured.episodeNumber)
            assertEquals(LocalDate.parse("2009-05-11"), savedEpisode.captured.originallyAvailableAt)

            verify(exactly = 1) { tvShowRepository.save(any()) }
            verify(exactly = 2) { tvEpisodeRepository.save(any()) }
            verify(exactly = 1) {
                tvEpisodeWatchCrudRepository.insertIgnore(3963, TvEpisodeWatchSource.PLEX.name, Instant.ofEpochSecond(1775146349))
            }
            verify(exactly = 1) { tvEpisodeRepository.save(match { it.id == 3963L && it.tvdbId == "588991" }) }
        }

    @Test
    fun `deve reutilizar show e episodio existentes por fingerprint e dados estruturados`() =
        runBlocking {
            val payload = episodePayload()
            val existingShow =
                TvShow(
                    id = 10,
                    originalTitle = "Big Bang: A Teoria",
                    year = null,
                    slug = null,
                    fingerprint = "show-fp",
                )
            val existingEpisode =
                TvEpisode(
                    id = 20,
                    showId = 10,
                    title = "A Expedicao Monopolar",
                    seasonNumber = 2,
                    episodeNumber = 23,
                    fingerprint = "episode-fp",
                )

            every { tvShowRepository.findByFingerprint(any()) } returns existingShow
            every { tvEpisodeRepository.findByFingerprint(any()) } returns existingEpisode
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { tvEpisodeWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
            every { tvShowRepository.save(any()) } answers { firstArg() }
            every { tvEpisodeRepository.save(any()) } answers { firstArg() }

            val result = service.processScrobble(payload)

            assertNotNull(result)
            assertEquals(20, result.episodeId)
            verify(exactly = 1) { tvShowRepository.save(match { it.id == 10L && it.slug == "the-big-bang-theory" && it.year == 2009 }) }
            verify(exactly = 2) { tvEpisodeRepository.save(any()) }
            verify(exactly = 1) {
                tvEpisodeRepository.save(
                    match { it.id == 20L && it.tmdbId == "64673" && it.tvdbId == "588991" && it.imdbId == "tt1426233" },
                )
            }
            verify(exactly = 1) { tvShowRepository.findByFingerprint(any()) }
            verify(exactly = 1) { tvEpisodeRepository.findByFingerprint(any()) }
        }

    @Test
    fun `deve ignorar payload nao episode`() =
        runBlocking {
            val payload = episodePayload(type = "movie")
            val result = service.processScrobble(payload)
            assertNull(result)
        }

    @Test
    fun `deve ignorar payload nao scrobble`() =
        runBlocking {
            val payload = episodePayload(event = "media.play")
            val result = service.processScrobble(payload)
            assertNull(result)
        }

    @Test
    fun `deve usar grandparentTitle como titulo da serie quando originalTitle e do episodio`() =
        runBlocking {
            val payload =
                episodePayload(
                    grandparentTitle = "Classroom of the Elite",
                    originalTitle = "悪とは何か――弱さから 生ずるすべてのものだ。",
                    year = 2017,
                    grandparentSlug = "classroom-of-the-elite",
                )
            val savedShow = slot<TvShow>()
            val persistedShow =
                TvShow(
                    id = 10,
                    originalTitle = "Classroom of the Elite",
                    year = 2017,
                    slug = "classroom-of-the-elite",
                    fingerprint = "show-fp",
                )
            val persistedEpisode =
                TvEpisode(
                    id = 20,
                    showId = 10,
                    title = "A Expedicao Monopolar",
                    seasonNumber = 2,
                    episodeNumber = 23,
                    fingerprint = "episode-fp",
                )

            every { tvShowRepository.findByFingerprint(any()) } returns null
            every { tvShowRepository.save(capture(savedShow)) } returns persistedShow
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { tvEpisodeRepository.findByFingerprint(any()) } returns null
            every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(any(), any(), any()) } returns null
            every { tvEpisodeRepository.save(any()) } returns persistedEpisode
            every { tvEpisodeWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs

            service.processScrobble(payload)

            assertEquals("Classroom of the Elite", savedShow.captured.originalTitle)
        }

    @Test
    fun `deve criar outro show quando fingerprint divergir mesmo com slug igual`() =
        runBlocking {
            val payload =
                episodePayload(
                    grandparentTitle = "Frieren e a Jornada para o Alem",
                    originalTitle = "Frieren: Beyond Journey's End",
                    grandparentSlug = "frieren-beyond-journeys-end",
                    year = 2026,
                )
            val persistedEpisode =
                TvEpisode(
                    id = 20,
                    showId = 18,
                    title = "A Expedicao Monopolar",
                    seasonNumber = 2,
                    episodeNumber = 23,
                    fingerprint = "episode-fp",
                )
            val newShow =
                TvShow(
                    id = 18,
                    originalTitle = "Frieren e a Jornada para o Alem",
                    year = 2026,
                    slug = "frieren-beyond-journeys-end",
                    fingerprint = "new-show-fp",
                )

            every { tvShowRepository.findByFingerprint(any()) } returns null
            every { tvShowRepository.findAllBySlugAndYear("frieren-beyond-journeys-end", 2026) } returns
                listOf(
                    newShow.copy(id = 16),
                    newShow.copy(id = 17),
                )
            every { tvShowRepository.save(any()) } returns newShow
            every { tvEpisodeRepository.findByFingerprint(any()) } returns null
            every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(18, 2, 23) } returns null
            every { tvEpisodeRepository.save(any()) } returns persistedEpisode
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { tvEpisodeWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
            service.processScrobble(payload)

            verify(exactly = 1) { tvShowRepository.save(match { it.id == 0L && it.year == 2026 }) }
            verify(exactly = 1) { tvShowRepository.findByFingerprint(any()) }
        }

    @Test
    fun `deve reutilizar candidato unico por slug e ano quando ids e fingerprint nao resolverem`() =
        runBlocking {
            val payload =
                episodePayload(
                    grandparentTitle = "Kakegurui",
                    originalTitle = "\u3064\u307e\u3093\u306a\u3044\u5973",
                    year = 2017,
                    grandparentSlug = "kakegurui",
                )
            val existingShow =
                TvShow(
                    id = 86,
                    originalTitle = "\u8ced\u30b1\u30b0\u30eb\u30a4",
                    year = 2017,
                    slug = "kakegurui",
                    fingerprint = "canonical-show-fp",
                )
            val persistedEpisode =
                TvEpisode(
                    id = 2082,
                    showId = 86,
                    title = "A Expedicao Monopolar",
                    seasonNumber = 2,
                    seasonTitle = "Temporada 2",
                    episodeNumber = 23,
                    summary = "desc",
                    durationMs = 1260000,
                    originallyAvailableAt = LocalDate.parse("2009-05-11"),
                    fingerprint = "episode-fp",
                )

            every { tvShowRepository.findByFingerprint(any()) } returns null
            every { tvShowRepository.findAllBySlugAndYear("kakegurui", 2017) } returns listOf(existingShow)
            every { tvEpisodeRepository.findByFingerprint(any()) } returns null
            every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(86, 2, 23) } returns null
            every { tvEpisodeRepository.save(any()) } returns persistedEpisode
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { tvEpisodeWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs

            val result = service.processScrobble(payload)

            assertNotNull(result)
            assertEquals(2082, result.episodeId)
            verify(exactly = 0) { tvShowRepository.save(any()) }
            verify(exactly = 1) { tvShowRepository.findAllBySlugAndYear("kakegurui", 2017) }
        }

    @Test
    fun `deve reutilizar show canonico quando titulo do Plex divergir mas ids do episodio coincidirem`() =
        runBlocking {
            val payload =
                episodePayload(
                    grandparentTitle = "Kakegurui",
                    originalTitle = "\u3064\u307e\u3093\u306a\u3044\u5973",
                    year = 2017,
                    grandparentSlug = "kakegurui",
                )
            val canonicalShow =
                TvShow(
                    id = 86,
                    originalTitle = "\u8ced\u30b1\u30b0\u30eb\u30a4",
                    year = 2017,
                    slug = "kakegurui",
                    fingerprint = "canonical-show-fp",
                )
            val canonicalEpisode =
                TvEpisode(
                    id = 2082,
                    showId = 86,
                    title = "A Expedicao Monopolar",
                    seasonNumber = 2,
                    seasonTitle = "Temporada 2",
                    episodeNumber = 23,
                    summary = "desc",
                    durationMs = 1260000,
                    originallyAvailableAt = LocalDate.parse("2009-05-11"),
                    fingerprint = "canonical-episode-fp",
                    tmdbId = "64673",
                    tvdbId = "588991",
                    imdbId = "tt1426233",
                )

            every { tvEpisodeRepository.findByTmdbId("64673") } returns canonicalEpisode
            every { tvEpisodeRepository.findByTvdbId("588991") } returns canonicalEpisode
            every { tvEpisodeRepository.findByImdbId("tt1426233") } returns canonicalEpisode
            every { tvShowRepository.findById(86) } returns Optional.of(canonicalShow)
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { tvEpisodeWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs

            val result = service.processScrobble(payload)

            assertNotNull(result)
            assertEquals(2082, result.episodeId)
            verify(exactly = 0) { tvShowRepository.findByFingerprint(any()) }
            verify(exactly = 0) { tvShowRepository.save(any()) }
            verify(exactly = 0) { tvEpisodeRepository.findByFingerprint(any()) }
            verify(exactly = 0) { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(any(), any(), any()) }
            verify(exactly = 0) { tvEpisodeRepository.save(any()) }
            verify(exactly = 1) {
                tvEpisodeWatchCrudRepository.insertIgnore(2082, TvEpisodeWatchSource.PLEX.name, Instant.ofEpochSecond(1775146349))
            }
        }

    private fun episodePayload(
        event: String = "media.scrobble",
        type: String = "episode",
        grandparentTitle: String = "Big Bang: A Teoria",
        originalTitle: String = "The Big Bang Theory",
        year: Int? = 2009,
        grandparentSlug: String? = "the-big-bang-theory",
    ): PlexWebhookPayload =
        PlexWebhookPayload(
            event = event,
            metadata =
                PlexWebhookPayload.PlexMetadata(
                    librarySectionType = "show",
                    ratingKey = "3963",
                    key = "/library/metadata/3963",
                    type = type,
                    title = "A Expedicao Monopolar",
                    titleSort = "Expedicao Monopolar",
                    grandparentTitle = grandparentTitle,
                    parentTitle = "Temporada 2",
                    originalTitle = originalTitle,
                    grandparentSlug = grandparentSlug,
                    guid = "plex://episode/5d9c12796c3e37001ecfed0d",
                    parentGuid = "plex://season/602e67aa91bd55002cf855c8",
                    grandparentGuid = "plex://show/5d9c086c02391c001f5891b3",
                    thumb = "/library/metadata/3963/thumb/1774197851",
                    parentThumb = "/library/metadata/3940/thumb/1774197845",
                    parentIndex = 2,
                    index = 23,
                    year = year,
                    lastViewedAt = Instant.ofEpochSecond(1775146349),
                    duration = 1260000,
                    originallyAvailableAt = LocalDate.parse("2009-05-11"),
                    summary = "desc",
                    guidList =
                        listOf(
                            PlexWebhookPayload.PlexMetadata.PlexGuidMetadata(id = "imdb://tt1426233"),
                            PlexWebhookPayload.PlexMetadata.PlexGuidMetadata(id = "tmdb://64673"),
                            PlexWebhookPayload.PlexMetadata.PlexGuidMetadata(id = "tvdb://588991"),
                        ),
                ),
        )
}
