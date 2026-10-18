package dev.marcal.mediapulse.server.service.books

import dev.marcal.mediapulse.server.api.books.BookSeriesTagSyncDto
import dev.marcal.mediapulse.server.config.VisualLibraryProperties
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.repository.BookSeriesTagSyncRepository
import dev.marcal.mediapulse.server.util.TxUtil
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

@Service
class BookSeriesTagSyncService(
    private val repository: BookSeriesTagSyncRepository,
    private val client: VisualLibraryClient,
    private val properties: VisualLibraryProperties,
    private val tx: TxUtil,
) {
    private val running = AtomicBoolean(false)
    private val logger = LoggerFactory.getLogger(javaClass)

    fun state(bookId: Long): BookSeriesTagSyncDto =
        repository.state(bookId)
            ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Livro não encontrado.")

    fun retry(bookId: Long): BookSeriesTagSyncDto {
        val current = state(bookId)
        if (!current.hasRemoteCovers) return current
        if (properties.baseUrl.isBlank()) {
            throw ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                "Configure o Visual Library para atualizar as tags das capas.",
            )
        }
        tx.inTx { repository.retry(bookId) }
        return state(bookId)
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
                    logger.warn("Sincronização de tags de séries interrompida. bookId={} error={}", id, error.javaClass.simpleName)
                }
            }
        } finally {
            running.set(false)
        }
    }

    fun synchronize(bookId: Long) {
        val snapshot = tx.inTx { listOfNotNull(repository.claim(bookId)) }.firstOrNull() ?: return
        if (snapshot.targets.isEmpty()) {
            tx.inTx { repository.finish(snapshot, false, null) }
            return
        }
        val values =
            try {
                tags(snapshot.seriesNames)
            } catch (error: IllegalArgumentException) {
                tx.inTx { repository.finish(snapshot, true, error.message) }
                return
            }
        var failed = false
        var message: String? = null
        for (target in snapshot.targets) {
            if (!tx.inTx { repository.renew(snapshot) }) return

            try {
                val result = client.synchronizeBookSeries(target.assetId, target.entityType, target.entityId, values, snapshot.revision)
                if (!failed && result.preservedTags.isNotEmpty()) {
                    message = "Tags atualizadas. Tags pessoais, antigas ou de outras referências foram preservadas no Visual Library."
                }
            } catch (error: Exception) {
                failed = true
                message = (error as? ResponseStatusException)?.reason ?: "Não foi possível atualizar as tags das capas. Tente novamente."
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
                    "Uma série excede o limite de 48 caracteres por tag no Visual Library. Renomeie a série para sincronizar.",
                )
            }
            if (values.size >
                8
            ) {
                throw IllegalArgumentException(
                    "O livro pertence a mais de oito séries distintas, acima do limite de tags do Visual Library.",
                )
            }
            return values
        }
    }
}
