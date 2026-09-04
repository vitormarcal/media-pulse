package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.tv.ShowList
import org.springframework.data.repository.CrudRepository

interface ShowListRepository : CrudRepository<ShowList, Long> {
    fun findBySlug(slug: String): ShowList?

    fun findByNormalizedName(normalizedName: String): ShowList?
}
