package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.person.Person
import org.springframework.data.repository.CrudRepository

interface PersonRepository : CrudRepository<Person, Long> {
    fun findByTmdbId(tmdbId: String): Person?

    fun findBySlug(slug: String): Person?
}
