package dev.marcal.mediapulse.server.service.movie

import dev.marcal.mediapulse.server.api.movies.MovieCollectionTagSyncDto
import dev.marcal.mediapulse.server.config.VisualLibraryProperties
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.repository.MovieCollectionTagSyncRepository
import dev.marcal.mediapulse.server.util.TxUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Service
class MovieCollectionTagSyncService(
    private val repository: MovieCollectionTagSyncRepository,
    private val client: VisualLibraryClient,
    private val properties: VisualLibraryProperties,
    private val tx: TxUtil,
) {
    private val running = AtomicBoolean(false)
    private val logger = LoggerFactory.getLogger(javaClass)

    fun state(movieId: Long): MovieCollectionTagSyncDto =
        repository.state(movieId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Filme não encontrado.")

    fun retry(movieId: Long): MovieCollectionTagSyncDto {
        val current = state(movieId)
        if (!current.hasRemoteImages) return current
        if (properties.baseUrl.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Configure o Visual Library para atualizar as tags das imagens.",
            )
        }
        tx.inTx { repository.retry(movieId) }
        return state(movieId)
    }

    fun runBatch() {
        if (properties.baseUrl.isBlank() || !running.compareAndSet(false, true)) return
        try {
            for (id in repository.candidates(10)) {
                if (Thread.currentThread().isInterrupted) break
                try {
                    synchronize(id)
                } catch (error: Exception) {
                    // A claim expires after interruption or persistence failure, allowing another attempt.
                    logger.warn("Sincronização de tags de coleção interrompida. movieId={} error={}", id, error.javaClass.simpleName)
                }
            }
        } finally {
            running.set(false)
        }
    }

    fun synchronize(movieId: Long) {
        val snapshot = tx.inTx { listOfNotNull(repository.claim(movieId)) }.firstOrNull() ?: return
        if (snapshot.targets.isEmpty()) {
            tx.inTx { repository.finish(snapshot, false, null) }
            return
        }
        val values =
            try {
                tags(snapshot.collectionNames)
            } catch (error: IllegalArgumentException) {
                tx.inTx { repository.finish(snapshot, true, error.message) }
                return
            }
        var failed = false
        var message: String? = null
        for (target in snapshot.targets) {
            if (!tx.inTx { repository.renew(snapshot) }) return

            try {
                val result = client.synchronizeMovieCollection(target.assetId, target.entityId, values, snapshot.revision)
                if (!failed && result.preservedTags.isNotEmpty()) {
                    message = "Tags atualizadas. Tags pessoais, antigas ou de outras referências foram preservadas no Visual Library."
                }
            } catch (error: Exception) {
                failed = true
                message = (error as? ResponseStatusException)?.reason ?: "Não foi possível atualizar as tags das imagens. Tente novamente."
            }
        }
        tx.inTx { repository.finish(snapshot, failed, message) }
    }

    companion object {
        fun tags(names: List<String>): List<String> {
            val values =
                names
                    .map { it.trim().replace(Regex("(?U)\\s+"), " ") }
                    .filter { it.isNotEmpty() }
                    .distinctBy { it.lowercase(Locale.ROOT) }
            if (values.any {
                    it.length > 48
                }
            ) {
                throw IllegalArgumentException(
                    "Uma coleção excede o limite de 48 caracteres por tag no Visual Library. Renomeie a coleção para sincronizar.",
                )
            }
            if (values.size >
                8
            ) {
                throw IllegalArgumentException(
                    "A classificação excede o limite de oito tags do Visual Library.",
                )
            }
            return values
        }
    }
}
