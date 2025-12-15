package dev.marcal.mediapulse.server.service.musicbrainz

import dev.marcal.mediapulse.server.config.MusicBrainzProperties
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzClientException
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.Provider
import dev.marcal.mediapulse.server.model.music.GenreSource
import dev.marcal.mediapulse.server.repository.crud.AlbumGenreSyncStateRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.repository.crud.ExternalIdentifierRepository
import dev.marcal.mediapulse.server.repository.query.AlbumQueryRepository
import dev.marcal.mediapulse.server.service.music.AlbumGenreService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.util.concurrent.atomic.AtomicBoolean

@Service
class MusicBrainzAlbumGenreEnrichmentService(
    private val props: MusicBrainzProperties,
    private val albumQuery: AlbumQueryRepository,
    private val albumRepo: AlbumRepository,
    private val extIds: ExternalIdentifierRepository,
    private val syncRepo: AlbumGenreSyncStateRepository,
    private val musicBrainzApiClient: MusicBrainzApiClient,
    private val albumGenreService: AlbumGenreService,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)
        private const val PROGRESS_EVERY = 25
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val running = AtomicBoolean(false)

    private data class Counters(
        var processed: Int = 0,
        var skippedAlreadyDone: Int = 0,
        var skippedNoMbid: Int = 0,
        var skippedAlbumMissing: Int = 0,
        var empty: Int = 0,
        var enriched: Int = 0,
        var failed: Int = 0,
    )

    sealed class Outcome {
        data object Enriched : Outcome()

        data object Empty : Outcome()

        data object SkippedDone : Outcome()

        data object SkippedNoMbid : Outcome()

        data object SkippedAlbumMissing : Outcome()

        data object Failed : Outcome()
    }

    fun enrichBatchAsync(limit: Int) {
        if (!running.compareAndSet(false, true)) return
        scope.launch {
            try {
                enrichBatch(limit)
            } finally {
                running.set(false)
            }
        }
    }

    suspend fun enrichBatch(limit: Int = props.enrich.batchSize): Int {
        if (!props.enabled) {
            logger.info("MusicBrainz enrichment skipped: disabled by config")
            return 0
        }

        val source = GenreSource.MUSICBRAINZ
        val albumIds = albumQuery.findAlbumIdsForGenreEnrichment(limit, props.enrich.onlyMissingAlbumGenres)

        logger.info("MusicBrainz enrichment started | limit={} | candidates={}", limit, albumIds.size)

        val c = Counters()

        for ((index, albumId) in albumIds.withIndex()) {
            val position = index + 1
            if (position % PROGRESS_EVERY == 0) logProgress(position, albumIds.size, c)

            val outcome = processOneAlbum(albumId, source)
            c.processed++

            when (outcome) {
                Outcome.Enriched -> c.enriched++
                Outcome.Empty -> c.empty++
                Outcome.SkippedDone -> c.skippedAlreadyDone++
                Outcome.SkippedNoMbid -> c.skippedNoMbid++
                Outcome.SkippedAlbumMissing -> c.skippedAlbumMissing++
                Outcome.Failed -> c.failed++
            }
        }

        logger.info(
            "MusicBrainz enrichment finished | candidates={} | processed={} | enriched={} | empty={} | skippedDone={} | skippedNoMbid={} | skippedAlbumMissing={} | failed={}",
            albumIds.size,
            c.processed,
            c.enriched,
            c.empty,
            c.skippedAlreadyDone,
            c.skippedNoMbid,
            c.skippedAlbumMissing,
            c.failed,
        )

        return c.processed
    }

    private fun logProgress(
        position: Int,
        total: Int,
        c: Counters,
    ) {
        logger.info(
            "MusicBrainz enrichment progress | at={}/{} | processed={} | enriched={} | skippedDone={} | skippedNoMbid={} | empty={} | failed={}",
            position,
            total,
            c.processed,
            c.enriched,
            c.skippedAlreadyDone,
            c.skippedNoMbid,
            c.empty,
            c.failed,
        )
    }

    private suspend fun processOneAlbum(
        albumId: Long,
        source: GenreSource,
    ): Outcome {
        if (!syncRepo.shouldFetch(albumId, source.name)) {
            logger.info("Album {} skipped | reason=ALREADY_DONE | source={}", albumId, source.name)
            return Outcome.SkippedDone
        }

        val externalIdentifier =
            extIds.findFirstByEntityTypeAndProviderAndEntityId(
                entityType = EntityType.ALBUM,
                entityId = albumId,
                provider = Provider.MUSICBRAINZ,
            )

        if (externalIdentifier == null) {
            logger.info("Album {} skipped | reason=NO_MBID", albumId)
            syncRepo.markDone(albumId, source.name, "NO_MBID")
            return Outcome.SkippedNoMbid
        }

        val mbid = externalIdentifier.externalId

        return try {
            val tags = musicBrainzApiClient.getAlbumGenreNamesByMbid(mbid, max = props.enrich.maxTags)

            val album = albumRepo.findById(albumId).orElse(null)
            if (album == null) {
                logger.warn("Album {} skipped | reason=ALBUM_NOT_FOUND_LOCAL | mbid={}", albumId, mbid)
                syncRepo.markDone(albumId, source.name, "ALBUM_NOT_FOUND_LOCAL")
                return Outcome.SkippedAlbumMissing
            }

            if (tags.isEmpty()) {
                logger.info("Album {} done | result=EMPTY_RESULT | title='{}' | mbid={}", albumId, album.title, mbid)
                syncRepo.markDone(albumId, source.name, "EMPTY_RESULT")
                Outcome.Empty
            } else {
                albumGenreService.addGenres(album, tags, source)
                syncRepo.markDone(albumId, source.name, null)
                logger.info("Album {} enriched | title='{}' | tagsCount={}", albumId, album.title, tags.size)
                Outcome.Enriched
            }
        } catch (e: MusicBrainzClientException.NotFound) {
            logger.warn("MusicBrainz not found | albumId={} | mbid={} | endpoint={}", albumId, mbid, e.endpoint, e)
            syncRepo.markDone(albumId, source.name, "MB_NOT_FOUND")
            Outcome.Failed
        } catch (e: MusicBrainzClientException.Retryable) {
            logger.warn("MusicBrainz retryable error | albumId={} | mbid={} | endpoint={}", albumId, mbid, e.endpoint, e)
            syncRepo.markFailed(albumId, source.name, "MB_RETRYABLE endpoint=${e.endpoint}", forceNext = true)
            Outcome.Failed
        } catch (e: MusicBrainzClientException.Fatal) {
            logger.warn("MusicBrainz fatal error | albumId={} | mbid={} | endpoint={}", albumId, mbid, e.endpoint, e)
            syncRepo.markFailed(albumId, source.name, "MB_FATAL endpoint=${e.endpoint}")
            Outcome.Failed
        } catch (e: Exception) {
            logger.warn("MusicBrainz enrichment failed | albumId={} | mbid={}", albumId, mbid, e)
            syncRepo.markFailed(albumId, source.name, e.message ?: e.javaClass.simpleName)
            Outcome.Failed
        }
    }
}
