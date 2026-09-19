package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.tv.TvEpisode
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShowEpisodesRefreshServiceTest {
    private val shows = mockk<TvShowRepository>()
    private val episodes = mockk<TvEpisodeRepository>()
    private val tmdb = mockk<TmdbApiClient>()
    private val tx = mockk<TxUtil>()
    private val service = ShowEpisodesRefreshService(shows, episodes, tmdb, tx)
    private val show = TvShow(id = 10, originalTitle = "Local show", fingerprint = "show", tmdbId = "100")
    private val stored = mutableListOf<TvEpisode>()

    @BeforeEach
    fun setup() {
        every { shows.findById(10) } returns Optional.of(show)
        every { shows.findByIdForUpdate(10) } returns show
        every { tx.inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() }
        every { episodes.findByShowIdAndSeasonNumberOrderByEpisodeNumberAscIdAsc(any(), any()) } answers {
            stored.filter { it.showId == firstArg<Long>() && it.seasonNumber == secondArg<Int>() }
        }
        every { episodes.findByTmdbId(any()) } answers { stored.find { it.tmdbId == firstArg<String>() } }
        every { episodes.findByFingerprint(any()) } answers { stored.find { it.fingerprint == firstArg<String>() } }
        every { episodes.findByShowIdAndSeasonNumberAndEpisodeNumber(any(), any(), any()) } answers {
            stored.find { it.showId == firstArg<Long>() && it.seasonNumber == secondArg<Int>() && it.episodeNumber == thirdArg<Int>() }
        }
        every { episodes.save(any()) } answers {
            firstArg<TvEpisode>().copy(id = stored.size.toLong() + 1).also(stored::add)
        }
        every { tmdb.fetchShowDetails("100") } returns details(0, 1, 2, 3)
        every { tmdb.fetchShowSeasonDetails("100", 1) } returns season(1, candidate(11, 1), candidate(12, 2))
        every { tmdb.fetchShowSeasonDetails("100", 2) } returns season(2, candidate(21, 1))
        every { tmdb.fetchShowSeasonDetails("100", 3) } returns season(3)
    }

    @Test
    fun `adds missing regular episodes including future dates and is idempotent`() {
        val existing =
            TvEpisode(id = 1, showId = 10, title = "Curated title", seasonNumber = 1, episodeNumber = 1, fingerprint = "existing")
        stored += existing
        val result = service.refresh(10)
        assertEquals(1, result.addedSeasonsCount)
        assertEquals(2, result.addedEpisodesCount)
        assertEquals(existing, stored.first())
        assertEquals(LocalDate.of(2099, 1, 1), stored.last().originallyAvailableAt)
        assertEquals("21", stored.last().tmdbId)
        assertEquals("Season 2", stored.last().seasonTitle)
        assertEquals(1_200_000, stored.last().durationMs)
        val repeated = service.refresh(10)
        assertEquals(0, repeated.addedSeasonsCount)
        assertEquals(0, repeated.addedEpisodesCount)
        assertEquals(3, stored.size)
        verify(exactly = 0) { tmdb.fetchShowSeasonDetails("100", 0) }
        verify(exactly = 0) { shows.save(any()) }
    }

    @Test
    fun `recognizes existing TMDb identity even with different local numbering`() {
        val existing =
            TvEpisode(
                id = 1,
                showId = 10,
                title = "Preserved",
                seasonNumber = 5,
                episodeNumber = 8,
                fingerprint = "existing",
                tmdbId = "11",
            )
        stored += existing
        every { tmdb.fetchShowDetails("100") } returns details(1)
        every { tmdb.fetchShowSeasonDetails("100", 1) } returns season(1, candidate(11, 1))
        assertEquals(0, service.refresh(10).addedEpisodesCount)
        assertEquals(listOf(existing), stored)
    }

    @Test
    fun `failed season fetch does not write any episodes`() {
        every { tmdb.fetchShowSeasonDetails("100", 2) } returns null
        assertEquals(HttpStatus.BAD_GATEWAY, assertFailsWith<ResponseStatusException> { service.refresh(10) }.statusCode)
        verify(exactly = 0) { episodes.save(any()) }
        verify(exactly = 0) { tx.inTx<Any>(any()) }
    }

    @Test
    fun `missing show and missing link fail without provider calls`() {
        every { shows.findById(10) } returns Optional.empty()
        assertEquals(HttpStatus.NOT_FOUND, assertFailsWith<ResponseStatusException> { service.refresh(10) }.statusCode)
        every { shows.findById(10) } returns Optional.of(show.copy(tmdbId = null))
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { service.refresh(10) }.statusCode)
        verify(exactly = 0) { tmdb.fetchShowDetails(any()) }
    }

    @Test
    fun `changed link and foreign episode identity are rejected`() {
        every { shows.findByIdForUpdate(10) } returns show.copy(tmdbId = "200")
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { service.refresh(10) }.statusCode)
        every { shows.findByIdForUpdate(10) } returns show
        stored += TvEpisode(id = 1, showId = 20, title = "Other show", fingerprint = "other", tmdbId = "11")
        assertEquals(HttpStatus.CONFLICT, assertFailsWith<ResponseStatusException> { service.refresh(10) }.statusCode)
        verify(exactly = 0) { episodes.save(any()) }
    }

    @Test
    fun `ignores invalid episode numbers and supports undated episodes`() {
        every { tmdb.fetchShowDetails("100") } returns details(1, 1)
        every { tmdb.fetchShowSeasonDetails("100", 1) } returns
            season(1, candidate(11, 0), candidate(12, 1).copy(airDate = null, title = null), candidate(13, 2).copy(episodeNumber = null))
        val result = service.refresh(10)
        assertEquals(1, result.addedEpisodesCount)
        assertEquals("Episódio 1", stored.single().title)
        assertEquals(null, stored.single().originallyAvailableAt)
        verify(exactly = 1) { tmdb.fetchShowSeasonDetails("100", 1) }
    }

    private fun details(vararg numbers: Int) =
        TmdbApiClient.TmdbShowDetails(
            null,
            null,
            null,
            null,
            null,
            null,
            numbers.map {
                TmdbApiClient.TmdbShowSeasonSummary(null, null, it, null, null, null)
            },
        )

    private fun season(
        number: Int,
        vararg candidates: TmdbApiClient.TmdbShowSeasonEpisode,
    ) = TmdbApiClient.TmdbShowSeasonDetails(null, "Season $number", null, number, null, null, candidates.toList())

    private fun candidate(
        id: Int,
        number: Int,
    ) = TmdbApiClient.TmdbShowSeasonEpisode(id.toString(), "Episode $number", "Summary", number, "2099-01-01", 20)
}
