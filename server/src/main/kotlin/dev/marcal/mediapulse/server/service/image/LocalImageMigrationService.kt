package dev.marcal.mediapulse.server.service.image

import dev.marcal.mediapulse.server.config.VisualLibraryProperties
import dev.marcal.mediapulse.server.integration.visuallibrary.VisualLibraryClient
import dev.marcal.mediapulse.server.model.image.LocalImageCleanup
import dev.marcal.mediapulse.server.model.image.LocalImageReference
import dev.marcal.mediapulse.server.model.image.LocalImageTarget
import dev.marcal.mediapulse.server.model.visuallibrary.VisualLibraryUploadMetadata
import dev.marcal.mediapulse.server.repository.crud.LocalImageMigrationRepository
import dev.marcal.mediapulse.server.service.artwork.ArtworkImageService
import dev.marcal.mediapulse.server.util.TxUtil
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.NoSuchFileException
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.BasicFileAttributes
import java.security.MessageDigest
import java.util.HexFormat
import java.util.concurrent.atomic.AtomicBoolean

@Service
class LocalImageMigrationService(
    private val properties: VisualLibraryProperties,
    private val repository: LocalImageMigrationRepository,
    private val client: VisualLibraryClient,
    private val artworks: ArtworkImageService,
    private val tx: TxUtil,
    @Value("\${media-pulse.storage.covers-path}") coversPath: String,
) {
    private val root = Path.of(coversPath).toAbsolutePath().normalize()
    private val running = AtomicBoolean(false)
    private val logger = LoggerFactory.getLogger(javaClass)

    fun runBatch() {
        if (!properties.migrationEnabled || !running.compareAndSet(false, true)) return
        try {
            repository.withWorkerLock {
                val candidates = repository.candidates(properties.migrationBatchSize)
                var migrated = 0
                var failed = 0
                for (url in candidates) {
                    if (Thread.currentThread().isInterrupted) break
                    try {
                        repository.recordAttempt(url)
                        if (migrate(url)) migrated++
                    } catch (error: Exception) {
                        failed++
                        logger.warn("Migração de imagem local falhou; arquivo preservado. url={} erro={}", url, error.javaClass.simpleName)
                    }
                }
                var removed = 0
                for (pending in repository.pendingCleanup(properties.migrationBatchSize)) {
                    if (Thread.currentThread().isInterrupted) break
                    try {
                        repository.cleanupAttempt(pending.localUrl)
                        if (cleanup(pending)) removed++
                    } catch (error: Exception) {
                        failed++
                        logger.warn(
                            "Limpeza local pendente; nova tentativa posterior. url={} erro={}",
                            pending.localUrl,
                            error.javaClass.simpleName,
                        )
                    }
                }
                if (candidates.isNotEmpty() || removed > 0 || failed > 0) {
                    logger.info(
                        "Migração Visual Library: processados={} migrados={} falhos={} removidos={}",
                        candidates.size,
                        migrated,
                        failed,
                        removed,
                    )
                }
            }
        } finally {
            running.set(false)
        }
    }

    private fun migrate(url: String): Boolean {
        val references = repository.references(url)
        if (references.isEmpty()) return false
        val bytes = read(path(url))
        val image = LibraryImageValidator.validate(bytes)
        val metadata = references.associateWith(::metadata)
        val uploaded =
            metadata.mapValues { (_, value) ->
                client.upload(image, value).also { verifyRemote(it, hash(bytes)) }
            }
        return tx.inTx {
            repository.lockReferences()
            if (repository.references(url) != references) return@inTx false
            // A movement edit made during upload must be retried with its newer revision.
            if (metadata.any { (reference, value) ->
                    reference.target == LocalImageTarget.ARTWORK &&
                        repository.artworkRevision(reference.entityId) != value.movementsRevision
                }
            ) {
                return@inTx false
            }
            check(read(path(url)).contentEquals(bytes)) { "Arquivo local mudou durante o upload." }
            uploaded.forEach { (reference, remoteUrl) -> repository.replace(reference, url, remoteUrl) }
            repository.recordMigrated(url, uploaded.values.first(), hash(bytes))
            true
        }
    }

    private fun metadata(reference: LocalImageReference): VisualLibraryUploadMetadata =
        when (reference.target) {
            LocalImageTarget.BOOK, LocalImageTarget.BOOK_EDITION ->
                VisualLibraryUploadMetadata.forEntity(
                    if (reference.target == LocalImageTarget.BOOK) "book" else "book_edition",
                    reference.entityId,
                    "livro",
                    repository.title(reference),
                )
            LocalImageTarget.ARTWORK -> artworks.artworkMetadata(reference.entityId)
            LocalImageTarget.ARTIST -> artworks.artistMetadata(reference.entityId)
            LocalImageTarget.MAGAZINE ->
                VisualLibraryUploadMetadata.forEntity("magazine_issue", reference.entityId, "revista", repository.title(reference))
            LocalImageTarget.GAME_COVER, LocalImageTarget.GAME_IMAGE ->
                VisualLibraryUploadMetadata.forEntity("game", reference.entityId, "jogo", repository.title(reference))
        }

    private fun cleanup(pending: LocalImageCleanup): Boolean {
        // Recheck availability even after a restart between the database commit and deletion.
        verifyRemote(pending.remoteUrl, pending.sha256)
        return tx.inTx {
            repository.lockReferencesForCleanup()
            if (repository.hasLocalReferences(pending.localUrl)) return@inTx false
            val path = path(pending.localUrl)
            val exists =
                try {
                    Files.readAttributes(path, BasicFileAttributes::class.java, LinkOption.NOFOLLOW_LINKS)
                    true
                } catch (_: NoSuchFileException) {
                    false
                }
            if (exists) {
                check(hash(read(path)) == pending.sha256) { "Arquivo local alterado; limpeza suspensa." }
                Files.delete(path)
            }
            repository.markCleaned(pending.localUrl)
            true
        }
    }

    private fun verifyRemote(
        url: String,
        expectedHash: String,
    ) {
        val id = requireNotNull(VisualLibraryClient.assetId(url)) { "Referência remota inválida." }
        val content = client.content(id, null, null)
        check(content.statusCode.is2xxSuccessful && content.body?.let(::hash) == expectedHash) { "Bytes remotos não conferem." }
    }

    private fun path(url: String): Path {
        require(url.startsWith("/covers/"))
        val relative = Path.of(url.removePrefix("/covers/"))
        require(!relative.isAbsolute && relative.nameCount > 0 && relative.none { it.toString() in setOf("..", ".") })
        val target = root.resolve(relative).normalize()
        require(target.startsWith(root) && target != root)
        // Reject symbolic links in the root and every child, including a missing final file during cleanup.
        var current = root.root
        for (part in target) {
            current = current.resolve(part)
            require(!Files.isSymbolicLink(current)) { "Links simbólicos não são elegíveis à migração." }
        }
        return target
    }

    private fun read(path: Path): ByteArray {
        require(Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
        return Files.newInputStream(path, StandardOpenOption.READ, LinkOption.NOFOLLOW_LINKS).use {
            it.readNBytes(LibraryImageStorageService.MAX_BYTES + 1).also { bytes ->
                require(bytes.isNotEmpty() && bytes.size <= LibraryImageStorageService.MAX_BYTES)
            }
        }
    }

    private fun hash(bytes: ByteArray): String = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes))
}
