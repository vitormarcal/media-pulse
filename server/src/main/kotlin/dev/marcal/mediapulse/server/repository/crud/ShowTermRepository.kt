package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.tv.ShowTerm
import dev.marcal.mediapulse.server.model.tv.ShowTermKind
import org.springframework.data.repository.CrudRepository

interface ShowTermRepository : CrudRepository<ShowTerm, Long> {
    fun findByKindAndNormalizedName(
        kind: ShowTermKind,
        normalizedName: String,
    ): ShowTerm?
}
