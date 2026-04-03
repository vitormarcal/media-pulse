package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ManualShowWatchCreateRequest
import dev.marcal.mediapulse.server.config.TmdbProperties
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.integration.tmdb.TmdbImageClient
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.image.ImageContent
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.model.tv.TvShowTitleSource
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowImageCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowTitleCrudRepository
import dev.marcal.mediapulse.server.service.image.ImageStorageService
import io.mockk.every
import io.mockk.just
import io.mockk.mockk
import io.mockk.runs
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.MediaType
import java.time.Instant
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ManualShowCatalogServiceTest {
    private lateinit var tvShowRepository: TvShowRepository
    private lateinit var tvShowTitleCrudRepository: TvShowTitleCrudRepository
    private lateinit var tvEpisodeRepository: TvEpisodeRepository
    private lateinit var tvShowImageCrudRepository: TvShowImageCrudRepository
    private lateinit var externalIdentifierRepository: ExternalIdentifierRepository
    private lateinit var tmdbApiClient: TmdbApiClient
    private lateinit var tmdbImageClient: TmdbImageClient
    private lateinit var imageStorageService: ImageStorageService
    private lateinit var service: ManualShowCatalogService

    @BeforeEach
    fun setUp() {
        tvShowRepository = mockk(relaxed = true)
        tvShowTitleCrudRepository = mockk(relaxed = true)
        tvEpisodeRepository = mockk(relaxed = true)
        tvShowImageCrudRepository = mockk(relaxed = true)
        externalIdentifierRepository = mockk(relaxed = true)
        tmdbApiClient = mockk(relaxed = true)
        tmdbImageClient = mockk(relaxed = true)
        imageStorageService = mockk(relaxed = true)
        every { tvShowRepository.save(any()) } answers { firstArg() as TvShow }
        every { tvEpisodeRepository.save(any()) } answers { firstArg() as TvEpisode }

        service =
            ManualShowCatalogService(
                tvShowRepository = tvShowRepository,
                tvShowTitleCrudRepository = tvShowTitleCrudRepository,
                tvEpisodeRepository = tvEpisodeRepository,
                tvShowImageCrudRepository = tvShowImageCrudRepository,
                externalIdentifierRepository = externalIdentifierRepository,
                tmdbApiClient = tmdbApiClient,
                tmdbImageClient = tmdbImageClient,
                imageStorageService = imageStorageService,
                tmdbProperties = TmdbProperties(imageBaseUrl = "https://image.tmdb.org"),
            )
    }

    @Test
    fun `nao duplica show para fingerprint repetido e reusa episodio`() {
        val show = TvShow(id = 11, originalTitle = "Severance", year = 2022, slug = "severance", fingerprint = "fp")
        val episode =
            TvEpisode(
                id = 22,
                showId = 11,
                title = "Good News About Hell",
                seasonNumber = 1,
                episodeNumber = 1,
                fingerprint = "ep-fp",
            )
        val request =
            ManualShowWatchCreateRequest(
                watchedAt = Instant.parse("2026-03-01T10:00:00Z"),
                showTitle = "Severance",
                episodeTitle = "Good News About Hell",
                year = 2022,
                seasonNumber = 1,
                episodeNumber = 1,
            )

        every { externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any()) } returns null
        every { tvShowRepository.findByFingerprint(any()) } returnsMany listOf(null, show)
        every { tvShowRepository.save(match { it.originalTitle == "Severance" && it.year == 2022 }) } returns show
        every { tvShowRepository.findById(11) } returns Optional.of(show)
        every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { tvEpisodeRepository.findByFingerprint(any()) } returnsMany listOf(null, episode)
        every {
            tvEpisodeRepository.save(
                match {
                    it.showId == 11L &&
                        it.title == "Good News About Hell" &&
                        it.seasonNumber == 1 &&
                        it.episodeNumber == 1
                },
            )
        } returns episode
        every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(11, 1, 1) } returns null
        every { tvShowImageCrudRepository.existsByShowIdAndIsPrimaryTrue(11) } returns true

        val first = service.resolveOrCreate(request)
        val second = service.resolveOrCreate(request)

        assertTrue(first.createdShow)
        assertTrue(first.createdEpisode)
        assertFalse(second.createdShow)
        assertFalse(second.createdEpisode)
        verify(exactly = 1) { tvShowRepository.save(any()) }
        verify(exactly = 1) { tvEpisodeRepository.save(any()) }
        verify(exactly = 2) {
            tvShowTitleCrudRepository.insertIgnore(
                11,
                "Severance",
                null,
                TvShowTitleSource.MANUAL.name,
                true,
            )
        }
    }

    @Test
    fun `prioriza match por tmdbId antes do fingerprint`() {
        val existingShow = TvShow(id = 77, originalTitle = "Severance", year = 2022, fingerprint = "fp-old")

        every {
            externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(EntityType.SHOW, Provider.TMDB, "95396")
        } returns ExternalIdentifier(entityType = EntityType.SHOW, entityId = 77, provider = Provider.TMDB, externalId = "95396")
        every { tmdbApiClient.fetchShowDetails("95396") } returns null
        every { tvShowRepository.findById(any()) } returns Optional.of(existingShow)
        every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { tvEpisodeRepository.findByFingerprint(any()) } returns null
        every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(any(), any(), any()) } returns null
        every { tvShowImageCrudRepository.existsByShowIdAndIsPrimaryTrue(77) } returns true
        every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns
            ExternalIdentifier(entityType = EntityType.SHOW, entityId = 77, provider = Provider.TMDB, externalId = "95396")

        val result =
            service.resolveOrCreate(
                ManualShowWatchCreateRequest(
                    watchedAt = Instant.parse("2026-03-01T10:00:00Z"),
                    showTitle = "Ruptura",
                    episodeTitle = "Good News About Hell",
                    year = 2024,
                    tmdbId = "95396",
                    seasonNumber = 1,
                    episodeNumber = 1,
                ),
            )

        assertEquals(77, result.show.id)
        verify(exactly = 0) { tvShowRepository.findByFingerprint(any()) }
    }

    @Test
    fun `insere capa tmdb apenas quando nao existe imagem primaria`() {
        val show = TvShow(id = 51, originalTitle = "Severance", year = 2022, slug = "severance", coverUrl = null, fingerprint = "fp")
        val episode =
            TvEpisode(
                id = 61,
                showId = 51,
                title = "Good News About Hell",
                seasonNumber = 1,
                episodeNumber = 1,
                fingerprint = "ep-fp",
            )

        every { externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any()) } returns null
        every { tvShowRepository.findByFingerprint(any()) } returns show
        every { tvShowRepository.findById(51) } returns
            Optional.of(show.copy(coverUrl = "/covers/tmdb/tv-shows/51/51_severance_poster.jpg"))
        every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { tvEpisodeRepository.findByFingerprint(any()) } returns episode
        every { tvShowImageCrudRepository.existsByShowIdAndIsPrimaryTrue(51) } returns false
        every { tmdbApiClient.fetchShowDetails("95396") } returns
            TmdbApiClient.TmdbShowDetails(
                title = "Severance",
                originalTitle = "Severance",
                overview = "desc",
                firstAirYear = 2022,
                posterPath = "/poster.jpg",
                backdropPath = "/backdrop.jpg",
            )
        every { tmdbImageClient.downloadImage("https://image.tmdb.org/t/p/w780/poster.jpg") } returns
            ImageContent(bytes = byteArrayOf(1, 2, 3), contentType = MediaType.IMAGE_JPEG)
        every { tmdbImageClient.downloadImage("https://image.tmdb.org/t/p/w780/backdrop.jpg") } returns
            ImageContent(bytes = byteArrayOf(4, 5, 6), contentType = MediaType.IMAGE_JPEG)
        every { imageStorageService.saveImageForTvShow(any(), "TMDB", 51, any()) } returnsMany
            listOf(
                "/covers/tmdb/tv-shows/51/51_severance_poster.jpg",
                "/covers/tmdb/tv-shows/51/51_severance_backdrop.jpg",
            )
        every { tvShowImageCrudRepository.insertIgnore(any(), any(), any()) } just runs
        every { tvShowRepository.save(match { it.id == 51L && it.coverUrl == "/covers/tmdb/tv-shows/51/51_severance_poster.jpg" }) } returns
            show.copy(coverUrl = "/covers/tmdb/tv-shows/51/51_severance_poster.jpg")
        every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
        every { externalIdentifierRepository.save(any()) } returns mockk()

        val result =
            service.resolveOrCreate(
                ManualShowWatchCreateRequest(
                    watchedAt = Instant.parse("2026-03-01T10:00:00Z"),
                    showTitle = "Severance",
                    episodeTitle = "Good News About Hell",
                    year = 2022,
                    tmdbId = "95396",
                    seasonNumber = 1,
                    episodeNumber = 1,
                ),
            )

        assertTrue(result.coverAssigned)
        verify(exactly = 1) {
            tvShowImageCrudRepository.insertIgnore(
                51,
                "/covers/tmdb/tv-shows/51/51_severance_poster.jpg",
                true,
            )
        }
        verify(exactly = 1) { tvShowRepository.save(match { it.id == 51L && it.coverUrl != null }) }
    }

    @Test
    fun `preenche description slug e year a partir do tmdb ao criar serie`() {
        val savedShow =
            TvShow(
                id = 90,
                originalTitle = "Severance",
                year = 2022,
                description = "Mark leads a team.",
                slug = "severance",
                coverUrl = null,
                fingerprint = "fp",
            )

        every { externalIdentifierRepository.findByEntityTypeAndProviderAndExternalId(any(), any(), any()) } returns null
        every { tmdbApiClient.fetchShowDetails("95396") } returns
            TmdbApiClient.TmdbShowDetails(
                title = "Severance",
                originalTitle = "Severance",
                overview = "Mark leads a team.",
                firstAirYear = 2022,
                posterPath = null,
                backdropPath = null,
            )
        every { tvShowRepository.findByFingerprint(any()) } returns null
        every { tvShowRepository.save(any()) } returns savedShow
        every { tvShowRepository.findById(90) } returns Optional.of(savedShow)
        every { tvShowTitleCrudRepository.insertIgnore(any(), any(), any(), any(), any()) } just runs
        every { tvEpisodeRepository.findByFingerprint(any()) } returns null
        every { tvEpisodeRepository.findByShowIdAndSeasonNumberAndEpisodeNumber(any(), any(), any()) } returns null
        every { externalIdentifierRepository.findByProviderAndExternalId(any(), any()) } returns null
        every { externalIdentifierRepository.save(any()) } returns mockk()

        service.resolveOrCreate(
            ManualShowWatchCreateRequest(
                watchedAt = Instant.parse("2026-03-01T10:00:00Z"),
                showTitle = "Ruptura",
                episodeTitle = "Good News About Hell",
                tmdbId = "95396",
                seasonNumber = 1,
                episodeNumber = 1,
                originallyAvailableAt = LocalDate.parse("2022-02-18"),
            ),
        )

        verify(exactly = 1) {
            tvShowRepository.save(
                match {
                    it.originalTitle == "Severance" &&
                        it.year == 2022 &&
                        it.description == "Mark leads a team." &&
                        it.slug == "severance"
                },
            )
        }
    }
}
