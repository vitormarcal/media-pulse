package dev.marcal.mediapulse.server.repository.crud

import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.ExternalIdentifier
import dev.marcal.mediapulse.server.model.Provider
import org.springframework.data.repository.CrudRepository

interface ExternalIdentifierRepository : CrudRepository<ExternalIdentifier, Long> {
    fun findByProviderAndExternalId(
        provider: Provider,
        externalId: String,
    ): ExternalIdentifier?

    fun findByEntityTypeAndEntityId(
        entityType: EntityType,
        entityId: Long,
    ): List<ExternalIdentifier>
}
