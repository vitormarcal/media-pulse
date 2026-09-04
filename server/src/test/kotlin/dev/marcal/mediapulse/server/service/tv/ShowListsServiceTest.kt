package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowListAttachRequest
import dev.marcal.mediapulse.server.api.shows.ShowListCoverUpdateRequest
import dev.marcal.mediapulse.server.api.shows.ShowListCreateRequest
import dev.marcal.mediapulse.server.api.shows.ShowListOrderUpdateRequest
import dev.marcal.mediapulse.server.api.shows.ShowListSummaryDto
import dev.marcal.mediapulse.server.model.tv.ShowList
import dev.marcal.mediapulse.server.model.tv.TvShow
import dev.marcal.mediapulse.server.repository.ShowListQueryRepository
import dev.marcal.mediapulse.server.repository.crud.ShowListItemCrudRepository
import dev.marcal.mediapulse.server.repository.crud.ShowListRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.web.server.ResponseStatusException
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class ShowListsServiceTest {
    private val shows = mockk<TvShowRepository>()
    private val lists = mockk<ShowListRepository>(relaxed = true)
    private val items = mockk<ShowListItemCrudRepository>(relaxed = true)
    private val query = mockk<ShowListQueryRepository>()
    private val service = ShowListsService(shows, lists, items, query)
    private val list = ShowList(id = 2, name = "Favoritas", normalizedName = "favoritas", slug = "favoritas")

    @Test
    fun `create normalizes and persists list`() {
        every { lists.findByNormalizedName("favoritas") } returns null
        every { lists.save(any()) } answers { firstArg<ShowList>().copy(id = 2) }
        every { query.summary(2) } returns ShowListSummaryDto(2, "Favoritas", "favoritas", null, 0)

        assertEquals(2, service.create(ShowListCreateRequest("  Favoritas ")).listId)
        verify { lists.save(match { it.name == "Favoritas" && it.normalizedName == "favoritas" }) }
    }

    @Test
    fun `attach is idempotent through item upsert`() {
        every { shows.findById(7) } returns Optional.of(TvShow(id = 7, originalTitle = "Severance", fingerprint = "fp"))
        every { lists.findById(2) } returns Optional.of(list)
        every { lists.save(any()) } answers { firstArg() }
        every { query.summary(2) } returns ShowListSummaryDto(2, "Favoritas", "favoritas", null, 1)

        service.attach(7, ShowListAttachRequest(listId = 2))

        verify(exactly = 1) { items.upsert(2, 7) }
    }

    @Test
    fun `update order persists every changed position`() {
        every { lists.findById(2) } returns Optional.of(list)
        every { lists.save(any()) } answers { firstArg() }
        every { items.positions(2) } returns listOf(ShowListItemCrudRepository.Position(7, 1), ShowListItemCrudRepository.Position(8, 2))

        service.updateOrder(2, ShowListOrderUpdateRequest(listOf(8, 7)))

        verify { items.updatePosition(2, 8, 1) }
        verify { items.updatePosition(2, 7, 2) }
    }

    @Test
    fun `cover must belong to list`() {
        every { lists.findById(2) } returns Optional.of(list)
        every { lists.save(any()) } answers { firstArg() }
        every { items.positions(2) } returns listOf(ShowListItemCrudRepository.Position(7, 1))
        every { query.summary(2) } returns ShowListSummaryDto(2, "Favoritas", "favoritas", null, 1, coverShowId = 7)

        val result = service.updateCover(2, ShowListCoverUpdateRequest(7))

        assertEquals(7, result.coverShowId)
        verify { lists.save(match { it.coverShowId == 7L }) }
    }

    @Test
    fun `cover rejects show outside list`() {
        every { lists.findById(2) } returns Optional.of(list)
        every { items.positions(2) } returns listOf(ShowListItemCrudRepository.Position(7, 1))

        val error = assertFailsWith<ResponseStatusException> { service.updateCover(2, ShowListCoverUpdateRequest(99)) }

        assertEquals(400, error.statusCode.value())
        verify(exactly = 0) { lists.save(any()) }
    }

    @Test
    fun `order rejects partial item set`() {
        every { lists.findById(2) } returns Optional.of(list)
        every { items.positions(2) } returns listOf(ShowListItemCrudRepository.Position(7, 1), ShowListItemCrudRepository.Position(8, 2))

        val error = assertFailsWith<ResponseStatusException> { service.updateOrder(2, ShowListOrderUpdateRequest(listOf(7))) }

        assertEquals(400, error.statusCode.value())
        verify(exactly = 0) { items.updatePosition(any(), any(), any()) }
    }
}
