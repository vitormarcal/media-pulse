package dev.marcal.mediapulse.server.model.visuallibrary

data class VisualLibrarySourceReference(
    val entityType: String,
    val entityId: String,
)

data class VisualLibraryUploadMetadata(
    val sourceReference: VisualLibrarySourceReference,
    val tags: List<String?> = emptyList(),
    val movements: List<String> = emptyList(),
    val movementsRevision: Long = 1,
) {
    companion object {
        fun forEntity(
            entityType: String,
            entityId: Long,
            vararg tags: String?,
        ): VisualLibraryUploadMetadata {
            require(entityId > 0) { "Referência de origem exige uma entidade persistida." }
            return VisualLibraryUploadMetadata(VisualLibrarySourceReference(entityType, entityId.toString()), tags.toList())
        }
    }
}
