package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.repository.PersonFilmographyRepository.MediaType
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull

class PersonFilmographyRepositoryTest {
    @Test
    fun `compaction removes only members without a local movie`() {
        val entityManager = mockk<EntityManager>()
        val lockQuery = mockk<Query>(relaxed = true)
        val markQuery = mockk<Query>(relaxed = true)
        val deleteQuery = mockk<Query>(relaxed = true)
        every { entityManager.createNativeQuery(match { it.contains("FOR UPDATE") }) } returns lockQuery
        every { entityManager.createNativeQuery(match { it.contains("UPDATE person_filmography_syncs") }) } returns markQuery
        every { entityManager.createNativeQuery(match { it.contains("DELETE FROM person_filmography_members") }) } returns deleteQuery
        every { lockQuery.setParameter(any<String>(), any()) } returns lockQuery
        every { markQuery.setParameter(any<String>(), any()) } returns markQuery
        every { deleteQuery.setParameter(any<String>(), any()) } returns deleteQuery
        every { lockQuery.resultList } returns listOf(44L)
        every { markQuery.executeUpdate() } returns 1
        every { deleteQuery.executeUpdate() } returns 17
        val repository = PersonFilmographyRepository(entityManager)

        val result = repository.compactIfEligible(44, MediaType.MOVIE)

        assertNotNull(result)
        assertEquals(17, result.removedMembers)
        verify {
            entityManager.createNativeQuery(
                match {
                    it.contains("NOT EXISTS") &&
                        it.contains("FROM movies local") &&
                        it.contains("local.tmdb_id = member.tmdb_id")
                },
            )
        }
    }

    @Test
    fun `ineligible person is not compacted`() {
        val entityManager = mockk<EntityManager>()
        val lockQuery = mockk<Query>(relaxed = true)
        every { entityManager.createNativeQuery(any<String>()) } returns lockQuery
        every { lockQuery.setParameter(any<String>(), any()) } returns lockQuery
        every { lockQuery.resultList } returns emptyList<Any>()
        val repository = PersonFilmographyRepository(entityManager)

        val result = repository.compactIfEligible(44, MediaType.SHOW)

        assertEquals(null, result)
        verify(exactly = 1) { entityManager.createNativeQuery(any<String>()) }
    }
}
