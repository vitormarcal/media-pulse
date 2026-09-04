package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowTermDto
import dev.marcal.mediapulse.server.api.shows.ShowTermKindDto
import dev.marcal.mediapulse.server.api.shows.ShowTermSourceDto
import dev.marcal.mediapulse.server.integration.tmdb.TmdbApiClient
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.TvShowQueryRepository
import dev.marcal.mediapulse.server.repository.crud.ShowTermAssignmentRepository
import dev.marcal.mediapulse.server.repository.crud.ShowTermRepository
import dev.marcal.mediapulse.server.repository.crud.ShowTermsCrudRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.transaction.support.TransactionTemplate
import java.util.Optional
import kotlin.test.assertEquals

class ShowTermsServiceTest {
    @Test
    fun `sync imports genres and keywords and marks success`() {
        val shows = mockk<TvShowRepository>()
        val query = mockk<TvShowQueryRepository>()
        val terms = mockk<ShowTermRepository>()
        val assignments = mockk<ShowTermAssignmentRepository>(relaxed = true)
        val state = mockk<ShowTermsCrudRepository>(relaxed = true)
        val tmdb = mockk<TmdbApiClient>()
        val service = ShowTermsService(shows, query, terms, assignments, state, tmdb, mockk<TransactionTemplate>())
        val show = TvShow(id = 7, originalTitle = "Dark", fingerprint = "dark", tmdbId = "70523")
        var id = 0L
        every { shows.findById(7) } returns Optional.of(show)
        every { terms.findByKindAndNormalizedName(any(), any()) } returns null
        every { terms.save(any()) } answers { firstArg<dev.marcal.mediapulse.server.model.tv.ShowTerm>().copy(id = ++id) }
        every { tmdb.fetchShowDetails("70523") } returns
            TmdbApiClient.TmdbShowDetails(
                title = "Dark",
                originalTitle = "Dark",
                overview = null,
                firstAirYear = 2017,
                posterPath = null,
                backdropPath = null,
                seasons = emptyList(),
                genres = listOf("Drama"),
                keywords = listOf("time travel"),
            )
        every { query.getShowTerms(7) } returns
            listOf(ShowTermDto(1, "Drama", "drama", ShowTermKindDto.GENRE, ShowTermSourceDto.TMDB, false, false, true))

        val response = service.syncFromTmdb(7)

        assertEquals(2, response.syncedCount)
        assertEquals(1, response.visibleCount)
        verify(exactly = 2) { assignments.upsert(7, any(), dev.marcal.mediapulse.server.model.tv.ShowTermSource.TMDB) }
        verify { state.markSuccess(7) }
    }
}
