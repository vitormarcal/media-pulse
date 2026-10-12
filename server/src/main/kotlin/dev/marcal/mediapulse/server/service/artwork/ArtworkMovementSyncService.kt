package dev.marcal.mediapulse.server.service.artwork

import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.repository.crud.ArtworkImageRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkRepository
import dev.marcal.mediapulse.server.util.TxUtil
import jakarta.persistence.EntityManager
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException

@Service
class ArtworkMovementSyncService(
    private val works: ArtworkRepository,
    private val images: ArtworkImageRepository,
    private val client: VisualLibraryClient,
    private val tx: TxUtil,
    private val entityManager: EntityManager,
) {
    fun synchronize(id: Long) {
        val snapshot =
            tx.inTx {
                // The preceding domain operation has committed; reload its latest revision.
                entityManager.clear()
                val work = works.lockById(id) ?: error("Obra não encontrada.")
                val assets = images.findByArtworkIdOrderByIdAsc(id).mapNotNull { VisualLibraryClient.assetId(it.url) }.distinct()
                works.updateMovementSync(id, work.movementsRevision, assets.isNotEmpty(), null)
                work to assets
            }
        val (work, assets) = snapshot
        if (assets.isEmpty()) return
        var message: String? = null
        var failed = false
        for (asset in assets) {
            try {
                val result = client.synchronizeMovements(asset, id, work.movements, work.movementsRevision)
                if (result.legacyTagsPreserved || result.preservedTags.isNotEmpty()) {
                    if (!failed) {
                        message =
                            "Movimentos atualizados. Tags pessoais, antigas sem procedência ou de outras referências foram preservadas no Visual Library."
                    }
                }
            } catch (error: Exception) {
                failed = true
                message =
                    (error as? ResponseStatusException)?.reason
                        ?: "Não foi possível atualizar os movimentos no Visual Library. Tente novamente."
            }
        }
        tx.inTx { works.updateMovementSync(id, work.movementsRevision, failed, message) }
    }
}
