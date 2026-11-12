package dev.marcal.mediapulse.server.service.hardcover

import dev.marcal.mediapulse.server.integration.hardcover.HardcoverApiClient
import dev.marcal.mediapulse.server.integration.hardcover.dto.HardcoverUserBook
import dev.marcal.mediapulse.server.repository.BookAuthorProfileRepository
import dev.marcal.mediapulse.server.repository.hardcover.HardcoverAuthorCandidateRepository
import dev.marcal.mediapulse.server.service.books.BookAuthorEnrichmentService
import dev.marcal.mediapulse.server.service.image.LibraryImageStorageService
import dev.marcal.mediapulse.server.util.TxUtil
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class HardcoverAuthorImportService(
    private val candidates: HardcoverAuthorCandidateRepository,
    private val profiles: BookAuthorProfileRepository,
    private val api: HardcoverApiClient,
    private val images: LibraryImageStorageService,
    private val tx: TxUtil,
) {
    private val logger = LoggerFactory.getLogger(javaClass)

    fun observe(item: HardcoverUserBook): Set<Long> = tx.inTx { candidates.observe(item) }

    // Called only after a complete catalog pass: see all same-name identities before choosing one.
    suspend fun enrich(authorIds: Set<Long>) {
        for (authorId in authorIds) {
            try {
                enrichOne(authorId)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                logger.warn("Hardcover author enrichment deferred | authorId={} error={}", authorId, ex.javaClass.simpleName)
            }
        }
    }

    private suspend fun enrichOne(authorId: Long) {
        val identity =
            tx.inTx {
                if (!profiles.lock(authorId) || profiles.isManual(authorId)) return@inTx 0L
                val id =
                    candidates.uniqueIdentity(authorId) ?: run {
                        logger.info("Hardcover author requires identity review | authorId={}", authorId)
                        return@inTx 0L
                    }
                if (profiles.find(authorId)?.hardcoverId?.let { it != id } == true ||
                    profiles.owner(id)?.let { it != authorId } == true
                ) {
                    return@inTx 0L
                }
                if (!candidates.claim(authorId, id)) return@inTx 0L
                id
            }
        if (identity == 0L) return
        delay(1500)
        val remote = api.fetchAuthorProfile(identity, null) ?: return
        if (remote.id != identity || remote.name.isBlank()) return
        val proposed = BookAuthorEnrichmentService.mapProfile(remote)
        val current = profiles.find(authorId)
        var photo: String? = null
        if (current?.photoUrl == null && proposed.photoSourceUrl != null) {
            try {
                photo = images.saveBookAuthor(images.downloadProviderImage(proposed.photoSourceUrl), authorId, proposed.externalName)
            } catch (ex: CancellationException) {
                throw ex
            } catch (ex: Exception) {
                logger.warn("Hardcover author portrait deferred | authorId={} error={}", authorId, ex.javaClass.simpleName)
            }
        }
        tx.inTx {
            if (!profiles.lock(authorId) ||
                profiles.isManual(authorId) ||
                candidates.uniqueIdentity(authorId) != identity
            ) {
                return@inTx false
            }
            if (profiles.owner(identity)?.let { it != authorId } == true) return@inTx false
            val saved = profiles.find(authorId)
            if (saved != null && saved.hardcoverId != identity) return@inTx false
            profiles.saveAutomatically(
                authorId,
                proposed.copy(
                    biography = saved?.biography ?: proposed.biography,
                    born = saved?.born ?: proposed.born,
                    died = saved?.died ?: proposed.died,
                    location = saved?.location ?: proposed.location,
                    links = saved?.links?.takeIf { it.isNotEmpty() } ?: proposed.links,
                    photoUrl = saved?.photoUrl ?: photo,
                    photoSourceUrl = saved?.photoSourceUrl ?: proposed.photoSourceUrl?.takeIf { photo != null },
                ),
            )
            true
        }
    }
}
