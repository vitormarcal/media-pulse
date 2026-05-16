package dev.marcal.mediapulse.server.service.comment

import dev.marcal.mediapulse.server.api.comments.CreateMediaCommentRequest
import dev.marcal.mediapulse.server.api.comments.UpdateMediaCommentRequest
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.comment.MediaComment
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.BookRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import dev.marcal.mediapulse.server.repository.crud.MediaCommentRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.Instant
import java.util.Optional
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MediaCommentServiceTest {
    private val mediaCommentRepository = mockk<MediaCommentRepository>()
    private val movieRepository = mockk<MovieRepository>()
    private val tvShowRepository = mockk<TvShowRepository>()
    private val albumRepository = mockk<AlbumRepository>()
    private val bookRepository = mockk<BookRepository>()
    private val gameRepository = mockk<GameRepository>()

    private val service =
        MediaCommentService(
            mediaCommentRepository = mediaCommentRepository,
            movieRepository = movieRepository,
            tvShowRepository = tvShowRepository,
            albumRepository = albumRepository,
            bookRepository = bookRepository,
            gameRepository = gameRepository,
        )

    @Test
    fun `cria comentario manual para album`() {
        val commentedAt = Instant.parse("2026-05-03T19:30:00Z")

        every { albumRepository.existsById(17) } returns true
        every { mediaCommentRepository.save(any<MediaComment>()) } answers {
            firstArg<MediaComment>().copy(id = 900)
        }

        val response =
            service.create(
                mediaType = "albums",
                entityId = 17,
                request = CreateMediaCommentRequest(body = "Voltei para o disco e ele abriu inteiro.", commentedAt = commentedAt),
            )

        assertEquals(900, response.id)
        assertEquals("Voltei para o disco e ele abriu inteiro.", response.body)
        assertEquals(commentedAt, response.commentedAt)
        assertTrue(!response.edited)
        verify(exactly = 1) { mediaCommentRepository.save(any<MediaComment>()) }
    }

    @Test
    fun `edita comentario existente preservando alvo`() {
        val original =
            MediaComment(
                id = 41,
                entityType = EntityType.MOVIE,
                entityId = 58,
                body = "Primeira leitura.",
                commentedAt = Instant.parse("2025-01-10T21:00:00Z"),
                createdAt = Instant.parse("2025-01-10T21:05:00Z"),
                updatedAt = Instant.parse("2025-01-10T21:05:00Z"),
            )

        every { mediaCommentRepository.findById(41) } returns Optional.of(original)
        every { mediaCommentRepository.save(any<MediaComment>()) } answers { firstArg() }

        val response =
            service.update(
                commentId = 41,
                request = UpdateMediaCommentRequest(body = "Depois de rever, a ironia ficou muito mais forte."),
            )

        assertEquals(41, response.id)
        assertEquals("Depois de rever, a ironia ficou muito mais forte.", response.body)
        assertTrue(response.edited)
        verify(exactly = 1) {
            mediaCommentRepository.save(
                withArg { saved ->
                    assertEquals(EntityType.MOVIE, saved.entityType)
                    assertEquals(58, saved.entityId)
                },
            )
        }
    }
}
