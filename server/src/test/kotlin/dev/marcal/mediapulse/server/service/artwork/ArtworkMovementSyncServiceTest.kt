package dev.marcal.mediapulse.server.service.artwork

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.artwork.Artwork
import dev.marcal.mediapulse.server.model.artwork.ArtworkImage
import dev.marcal.mediapulse.server.repository.crud.ArtworkImageRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkRepository
import dev.marcal.mediapulse.server.util.TxUtil
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

class ArtworkMovementSyncServiceTest {
    private val works = mockk<ArtworkRepository>()
    private val images = mockk<ArtworkImageRepository>()
    private val client = mockk<VisualLibraryClient>()
    private val tx = mockk<TxUtil>()
    private val service = ArtworkMovementSyncService(works, images, client, tx, mockk(relaxed = true))
    private val first = UUID.fromString("00000000-0000-0000-0000-000000000001")
    private val second = UUID.fromString("00000000-0000-0000-0000-000000000002")
    private val work = Artwork(id = 7, title = "The Kiss", movements = listOf("Art Nouveau", "Simbolismo"), movementsRevision = 4)

    init {
        every { tx.inTx<Any>(any()) } answers { firstArg<() -> Any>().invoke() }
        every { works.lockById(7) } returns work
        every { works.updateMovementSync(any(), any(), any(), any()) } returns 1
    }

    @Test
    fun `partial failure persists pending state with its revision and retry reaches all distinct remote assets`() {
        every { images.findByArtworkIdOrderByIdAsc(7) } returns
            listOf(
                ArtworkImage(id = 1, artworkId = 7, url = "/api/image-assets/$first/content"),
                ArtworkImage(id = 2, artworkId = 7, url = "/api/image-assets/$second/content"),
                ArtworkImage(id = 3, artworkId = 7, url = "/api/image-assets/$first/content"),
                ArtworkImage(id = 4, artworkId = 7, url = "/covers/artworks/local.webp"),
            )
        every { client.synchronizeMovements(first, 7, work.movements, 4) } returns VisualLibraryClient.MovementSyncResponse()
        every { client.synchronizeMovements(second, 7, work.movements, 4) } throws
            ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "offline")
        service.synchronize(7)
        verify(exactly = 1) { client.synchronizeMovements(first, 7, work.movements, 4) }
        verify { works.updateMovementSync(7, 4, true, "offline") }
        every { client.synchronizeMovements(second, 7, work.movements, 4) } returns VisualLibraryClient.MovementSyncResponse()
        service.synchronize(7)
        verify { works.updateMovementSync(7, 4, false, null) }
    }

    @Test
    fun `local-only works clear pending state without contacting Visual Library`() {
        every { images.findByArtworkIdOrderByIdAsc(7) } returns listOf(ArtworkImage(artworkId = 7, url = "/covers/artworks/local.webp"))
        service.synchronize(7)
        verify { works.updateMovementSync(7, 4, false, null) }
        verify(exactly = 0) { client.synchronizeMovements(any(), any(), any(), any()) }
    }
}
