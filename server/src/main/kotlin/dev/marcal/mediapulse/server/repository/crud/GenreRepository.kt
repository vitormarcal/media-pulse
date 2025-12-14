package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.music.Genre
import org.springframework.data.repository.CrudRepository

interface GenreRepository : CrudRepository<Genre, Long> {
    fun findByName(name: String): Genre?
}
