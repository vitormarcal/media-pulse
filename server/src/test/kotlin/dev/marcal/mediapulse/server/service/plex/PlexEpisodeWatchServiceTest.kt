package dev.marcal.mediapulse.server.service.plex

import dev.marcal.mediapulse.server.controller.webhook.dto.PlexWebhookPayload
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvEpisodeWatchSource
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class PlexEpisodeWatchServiceTest {
    private lateinit var tvShowRepository: TvShowRepository
    private lateinit var tvShowTitleCrudRepository: TvShowTitleCrudRepository
    private lateinit var tvEpisodeRepository: TvEpisodeRepository
    private lateinit var tvEpisodeWatchCrudRepository: TvEpisodeWatchCrudRepository
    private lateinit var externalIdentifierRepository: ExternalIdentifierRepository
    private lateinit var service: PlexEpisodeWatchService

    @BeforeEach
    fun setUp() {
        tvShowRepository = mockk(relaxed = true)
        tvShowTitleCrudRepository = mockk(relaxed = true)
        tvEpisodeRepository = mockk(relaxed = true)
        tvEpisodeWatchCrudRepository = mockk(relaxed = true)
        externalIdentifierRepository = mockk(relaxed = true)

        service =
            PlexEpisodeWatchService(
                tvShowRepository = tvShowRepository,
                tvShowTitleCrudRepository = tvShowTitleCrudRepository,
                tvEpisodeRepository = tvEpisodeRepository,
                tvEpisodeWatchCrudRepository = tvEpisodeWatchCrudRepository,
                externalIdentifierRepository = externalIdentifierRepository,
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

            every { tvShowRepository.findBySlug("the-big-bang-theory") } returns null
            every { tvShowRepository.findByShowTitle("Big Bang: A Teoria") } returns null
            every { tvShowRepository.save(capture(savedShow)) } returns persistedShow
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { tvEpisodeRepository.findByFingerprint(any()) } returns null
            every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(any(), any(), any()) } returns null
            every { tvEpisodeRepository.save(capture(savedEpisode)) } returns persistedEpisode
            every { tvEpisodeWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
            every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
            every { externalIdentifierRepository.save(any()) } answers { firstArg() }

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
            verify(exactly = 1) { tvEpisodeRepository.save(any()) }
            verify(exactly = 1) {
                tvEpisodeWatchCrudRepository.insertIgnore(3963, TvEpisodeWatchSource.PLEX.name, Instant.ofEpochSecond(1775146349))
            }
            verify(exactly = 1) {
                externalIdentifierRepository.save(
                    match {
                        it.entityType == EntityType.SHOW &&
                            it.provider == Provider.PLEX &&
                            it.externalId == "plex://show/5d9c086c02391c001f5891b3"
                    },
                )
            }
            verify(exactly = 1) {
                externalIdentifierRepository.save(
                    match {
                        it.entityType == EntityType.EPISODE &&
                            it.provider == Provider.TVDB &&
                            it.externalId == "588991"
                    },
                )
            }
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

            every { tvShowRepository.findBySlug("the-big-bang-theory") } returns existingShow
            every { tvEpisodeRepository.findByFingerprint(any()) } returns existingEpisode
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { tvEpisodeWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
            every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returnsMany
                listOf(mockk(), mockk(), null, null, null)
            every { externalIdentifierRepository.save(any()) } answers { firstArg() }
            every { tvShowRepository.save(any()) } answers { firstArg() }
            every { tvEpisodeRepository.save(any()) } answers { firstArg() }

            val result = service.processScrobble(payload)

            assertNotNull(result)
            assertEquals(20, result.episodeId)
            verify(exactly = 1) { tvShowRepository.save(match { it.id == 10L && it.slug == "the-big-bang-theory" && it.year == 2009 }) }
            verify(exactly = 1) { tvEpisodeRepository.save(any()) }
            verify(exactly = 1) { tvShowRepository.findBySlug("the-big-bang-theory") }
            verify(exactly = 0) { tvShowRepository.findByShowTitle(any()) }
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

            every { tvShowRepository.findBySlug("classroom-of-the-elite") } returns null
            every { tvShowRepository.findByShowTitle("Classroom of the Elite") } returns null
            every { tvShowRepository.save(capture(savedShow)) } returns persistedShow
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { tvEpisodeRepository.findByFingerprint(any()) } returns null
            every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(any(), any(), any()) } returns null
            every { tvEpisodeRepository.save(any()) } returns persistedEpisode
            every { tvEpisodeWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
            every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
            every { externalIdentifierRepository.save(any()) } answers { firstArg() }

            service.processScrobble(payload)

            assertEquals("Classroom of the Elite", savedShow.captured.originalTitle)
        }

    @Test
    fun `deve reutilizar show existente por slug mesmo com year divergente`() =
        runBlocking {
            val payload =
                episodePayload(
                    grandparentTitle = "Frieren e a Jornada para o Alem",
                    originalTitle = "Frieren: Beyond Journey's End",
                    grandparentSlug = "frieren-beyond-journeys-end",
                    year = 2026,
                )
            val existingShow =
                TvShow(
                    id = 17,
                    originalTitle = "葬送のフリーレン",
                    year = 2023,
                    slug = "frieren-beyond-journeys-end",
                    fingerprint = "show-fp",
                )
            val persistedEpisode =
                TvEpisode(
                    id = 20,
                    showId = 17,
                    title = "A Expedicao Monopolar",
                    seasonNumber = 2,
                    episodeNumber = 23,
                    fingerprint = "episode-fp",
                )

            every { tvShowRepository.findBySlug("frieren-beyond-journeys-end") } returns existingShow
            every { tvEpisodeRepository.findByFingerprint(any()) } returns null
            every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(17, 2, 23) } returns null
            every { tvEpisodeRepository.save(any()) } returns persistedEpisode
            every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
            every { tvEpisodeWatchCrudRepository.insertIgnore(any(), any(), any()) } just runs
            every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
            every { externalIdentifierRepository.save(any()) } answers { firstArg() }
            every { tvShowRepository.save(any()) } answers { firstArg() }

            service.processScrobble(payload)

            verify(exactly = 0) { tvShowRepository.save(any()) }
            verify(exactly = 0) { tvShowRepository.findByShowTitle(any()) }
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
