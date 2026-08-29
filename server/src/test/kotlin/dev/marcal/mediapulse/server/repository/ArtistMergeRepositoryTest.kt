package dev.marcal.mediapulse.server.repository

import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate

class ArtistMergeRepositoryTest {
    @Test
    fun `merge migrates historical aliases before deleting source artists`() {
        val jdbc = mockk<NamedParameterJdbcTemplate>(relaxed = true)
        every { jdbc.update(any<String>(), any<Map<String, *>>()) } returns 1
        every { jdbc.update(any<String>(), any<org.springframework.jdbc.core.namedparam.SqlParameterSource>()) } returns 1
        every { jdbc.queryForList(any<String>(), any<Map<String, *>>(), Int::class.java) } returns emptyList()
        val repository = ArtistMergeRepository(jdbc)

        repository.merge(
            ArtistMergeRepository.MergeCommand(
                targetId = 1,
                sourceIds = listOf(2),
                nameId = 1,
                imageId = 1,
                musicBrainzId = 1,
                ratingId = null,
                aliasIds = listOf(2),
                spotifyId = null,
                musicBrainzValue = null,
                fingerprint = "fingerprint",
            ),
        )

        verify {
            jdbc.update(
                match<String> { it.contains("FROM artist_name_aliases WHERE artist_id IN (:sourceIds)") },
                any<org.springframework.jdbc.core.namedparam.SqlParameterSource>(),
            )
        }
        verify {
            jdbc.update(
                match<String> { it.contains("DELETE FROM artists WHERE id IN (:sourceIds)") },
                any<org.springframework.jdbc.core.namedparam.SqlParameterSource>(),
            )
        }
    }
}
