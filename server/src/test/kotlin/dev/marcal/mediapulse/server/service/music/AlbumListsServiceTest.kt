package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.AlbumListDetailsResponse
import dev.marcal.mediapulse.server.api.music.AlbumListListenedRequest
import dev.marcal.mediapulse.server.api.music.AlbumListOrderRequest
import dev.marcal.mediapulse.server.api.music.AlbumListSummaryDto
import dev.marcal.mediapulse.server.model.music.AlbumList
import dev.marcal.mediapulse.server.repository.AlbumListQueryRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumListItemCrudRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumListRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals

class AlbumListsServiceTest {
    private val listRepository = mockk<AlbumListRepository>(relaxed = true)
    private val albumRepository = mockk<AlbumRepository>()
    private val itemRepository = mockk<AlbumListItemCrudRepository>(relaxed = true)
    private val queryRepository = mockk<AlbumListQueryRepository>()
    private val service = AlbumListsService(listRepository, albumRepository, itemRepository, queryRepository)
    private val list = AlbumList(id = 4, name = "Essenciais", normalizedName = "essenciais", slug = "essenciais")
    private val details = AlbumListDetailsResponse(4, "Essenciais", "essenciais", null, 3, 0, emptyList())

    @Test
    fun `lists for album delegates to the membership query`() {
        val expected =
            listOf(
                AlbumListSummaryDto(4, "Essenciais", "essenciais", null, 3, 1, emptyList(), Instant.EPOCH),
            )
        every { queryRepository.listsForAlbum(10) } returns expected

        assertEquals(expected, service.listsForAlbum(10))
        verify(exactly = 1) { queryRepository.listsForAlbum(10) }
    }

    @Test
    fun `update order requires and persists the complete item set`() {
        every { listRepository.findById(4) } returns Optional.of(list)
        every { listRepository.save(any()) } answers { firstArg() }
        every { queryRepository.itemIds(4) } returns listOf(10, 11, 12)
        every { queryRepository.details("essenciais") } returns details

        service.updateOrder(4, AlbumListOrderRequest(listOf(12, 10, 11)))

        verify { itemRepository.setPosition(4, 12, 1) }
        verify { itemRepository.setPosition(4, 10, 2) }
        verify { itemRepository.setPosition(4, 11, 3) }
    }

    @Test
    fun `unmark listened clears the contextual timestamp`() {
        every { listRepository.findById(4) } returns Optional.of(list)
        every { listRepository.save(any()) } answers { firstArg() }
        every { itemRepository.setListenedAt(4, 10, null) } returns 1
        every { queryRepository.details("essenciais") } returns details

        service.updateListened(4, 10, AlbumListListenedRequest(listened = false))

        verify(exactly = 1) { itemRepository.setListenedAt(4, 10, null) }
    }
}
