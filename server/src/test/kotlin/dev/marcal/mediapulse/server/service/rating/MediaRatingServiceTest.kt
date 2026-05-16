package dev.marcal.mediapulse.server.service.rating

import dev.marcal.mediapulse.server.api.ratings.UpsertMediaRatingRequest
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.rating.MediaRating
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.GameRepository
import dev.marcal.mediapulse.server.repository.crud.MediaRatingRepository
import dev.marcal.mediapulse.server.repository.crud.MovieRepository
import dev.marcal.mediapulse.server.repository.crud.TrackRepository
import dev.marcal.mediapulse.server.repository.crud.TvEpisodeRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import org.springframework.web.server.ResponseStatusException
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class MediaRatingServiceTest {
    private val mediaRatingRepository = mockk<MediaRatingRepository>()
    private val movieRepository = mockk<MovieRepository>()
    private val tvShowRepository = mockk<TvShowRepository>()
    private val tvEpisodeRepository = mockk<TvEpisodeRepository>()
    private val albumRepository = mockk<AlbumRepository>()
    private val trackRepository = mockk<TrackRepository>()
    private val gameRepository = mockk<GameRepository>()

    private val service =
        MediaRatingService(
            mediaRatingRepository = mediaRatingRepository,
            movieRepository = movieRepository,
            tvShowRepository = tvShowRepository,
            tvEpisodeRepository = tvEpisodeRepository,
            albumRepository = albumRepository,
            trackRepository = trackRepository,
            gameRepository = gameRepository,
        )

    @Test
    fun `creates new movie rating`() {
        every { movieRepository.existsById(42L) } returns true
        every { mediaRatingRepository.findByEntityTypeAndEntityId(EntityType.MOVIE, 42L) } returns null
        every { mediaRatingRepository.save(any()) } answers { firstArg() }

        val response = service.upsert("movies", 42L, UpsertMediaRatingRequest(rating = 5))

        assertEquals(5, response.rating)
        verify {
            mediaRatingRepository.save(
                match { it.entityType == EntityType.MOVIE && it.entityId == 42L && it.rating == 5.toShort() },
            )
        }
    }

    @Test
    fun `updates existing track rating`() {
        val current =
            MediaRating(
                id = 7L,
                entityType = EntityType.TRACK,
                entityId = 11L,
                rating = 2.toShort(),
            )
        every { trackRepository.existsById(11L) } returns true
        every { mediaRatingRepository.findByEntityTypeAndEntityId(EntityType.TRACK, 11L) } returns current
        every { mediaRatingRepository.save(any()) } answers { firstArg() }

        val response = service.upsert("tracks", 11L, UpsertMediaRatingRequest(rating = 4))

        assertEquals(4, response.rating)
        verify { mediaRatingRepository.save(match { it.id == 7L && it.rating == 4.toShort() }) }
    }

    @Test
    fun `clears existing episode rating`() {
        every { tvEpisodeRepository.existsById(99L) } returns true
        every { mediaRatingRepository.deleteByEntityTypeAndEntityId(EntityType.EPISODE, 99L) } returns Unit

        service.clear("episodes", 99L)

        verify { mediaRatingRepository.deleteByEntityTypeAndEntityId(EntityType.EPISODE, 99L) }
    }

    @Test
    fun `rejects rating outside scale`() {
        every { albumRepository.existsById(8L) } returns true

        val error =
            assertFailsWith<ResponseStatusException> {
                service.upsert("albums", 8L, UpsertMediaRatingRequest(rating = 6))
            }

        assertEquals(HttpStatus.BAD_REQUEST, error.statusCode)
    }
}
