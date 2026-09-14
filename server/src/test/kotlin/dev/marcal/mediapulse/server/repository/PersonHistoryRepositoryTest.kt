package dev.marcal.mediapulse.server.repository

import dev.marcal.mediapulse.server.api.people.PersonHistoryCategory
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import jakarta.persistence.EntityManager
import jakarta.persistence.Query
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PersonHistoryRepositoryTest {
    private val entityManager = mockk<EntityManager>()
    private val query = mockk<Query>()
    private val repository = PersonHistoryRepository(entityManager)

    @Test
    fun `cast ranking combines distinct watched movies and shows and excludes favorites`() {
        var sql = ""
        every { entityManager.createNativeQuery(any<String>()) } answers {
            sql = firstArg()
            query
        }
        every { query.setParameter(any<String>(), any()) } returns query
        every { query.resultList } returns
            listOf(
                arrayOf(1L, "Pessoa A", "pessoa-a", "/a.jpg", 5L, null),
                arrayOf(2L, "Pessoa B", "pessoa-b", null, 3L, null),
                arrayOf(3L, "Pessoa C", "pessoa-c", null, 2L, null),
            )

        val response = repository.findMostPresent(PersonHistoryCategory.CAST, limit = 2, offset = 0)

        assertEquals(listOf(1L, 2L), response.items.map { it.personId })
        assertEquals(2, response.nextOffset)
        assertTrue(sql.contains("GROUP BY mc.person_id, mc.movie_id"))
        assertTrue(sql.contains("GROUP BY sc.person_id, sc.show_id"))
        assertEquals(2, "credit_type = 'CAST'".toRegex().findAll(sql).count())
        assertTrue(sql.contains("p.favorited_at IS NULL"))
        assertTrue(sql.contains("HAVING COUNT(*) >= 2"))
        assertTrue(sql.contains("latest_watch DESC, p.name ASC, p.id ASC"))
        verify { query.setParameter("limitPlusOne", 3) }
        verify { query.setParameter("offset", 0) }
    }

    @Test
    fun `last page does not expose another offset`() {
        every { entityManager.createNativeQuery(any<String>()) } returns query
        every { query.setParameter(any<String>(), any()) } returns query
        every { query.resultList } returns listOf(arrayOf(1L, "Pessoa A", "pessoa-a", null, 2L, null))

        val response = repository.findMostPresent(PersonHistoryCategory.DIRECTING, limit = 8, offset = 8)

        assertEquals(1, response.items.size)
        assertNull(response.nextOffset)
    }

    @Test
    fun `writing ranking applies the jobs supported by movies and shows`() {
        var sql = ""
        every { entityManager.createNativeQuery(any<String>()) } answers {
            sql = firstArg()
            query
        }
        every { query.setParameter(any<String>(), any()) } returns query
        every { query.resultList } returns emptyList<Any>()

        repository.findMostPresent(PersonHistoryCategory.WRITING, limit = 4, offset = 0)

        assertTrue(sql.contains("mc.job IN ('Writer', 'Screenplay', 'Story')"))
        assertTrue(sql.contains("sc.job IN ('Writer', 'Screenplay', 'Story Editor')"))
    }
}
