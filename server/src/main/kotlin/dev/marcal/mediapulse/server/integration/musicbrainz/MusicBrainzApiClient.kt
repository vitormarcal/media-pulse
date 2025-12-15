package dev.marcal.mediapulse.server.integration.musicbrainz

import dev.marcal.mediapulse.server.config.MusicBrainzProperties
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseGroupResponse
import dev.marcal.mediapulse.server.integration.musicbrainz.dto.MbReleaseResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.reactor.awaitSingle
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.util.UriComponentsBuilder
import java.util.concurrent.ThreadLocalRandom

@Component
class MusicBrainzApiClient(
    private val musicBrainzWebClient: WebClient,
    private val props: MusicBrainzProperties,
) {
    companion object {
        private val logger = LoggerFactory.getLogger(this::class.java)

        private const val MAX_ATTEMPTS = 3 // 1 + 2 retries
        private val RETRYABLE_HTTP = setOf(429, 502, 503, 504)
    }

    private val mutex = Mutex()
    private var lastRequestAtMs: Long = 0

    private suspend fun throttle() {
        mutex.withLock {
            val now = System.currentTimeMillis()
            val wait = props.enrich.minRequestIntervalMs - (now - lastRequestAtMs)
            if (wait > 0) {
                logger.info("MusicBrainz throttle | waiting={}ms", wait)
                delay(wait)
            }
            lastRequestAtMs = System.currentTimeMillis()
        }
    }

    private fun url(
        path: String,
        inc: String,
    ): String =
        UriComponentsBuilder
            .fromHttpUrl(props.baseUrl)
            .path(path)
            .queryParam("inc", inc)
            .queryParam("fmt", "json")
            .build()
            .toUriString()

    private fun jitterMs(max: Long): Long = ThreadLocalRandom.current().nextLong(0, max + 1)

    private fun computeBackoffMs(retryNumber: Int): Long {
        // retryNumber: 1 (primeiro retry), 2 (segundo retry)
        val base =
            when (retryNumber) {
                1 -> 1_000L
                2 -> 2_000L
                else -> 4_000L
            }
        return base + jitterMs(350)
    }

    private suspend fun <T> mbGet(
        path: String,
        inc: String,
        clazz: Class<T>,
        mbid: String,
    ): T {
        val fullUrl = url(path, inc)

        var attempt = 0
        while (true) {
            attempt++

            throttle()
            logger.info("MusicBrainz request | GET {} | mbid={} | attempt={}/{}", fullUrl, mbid, attempt, MAX_ATTEMPTS)

            try {
                val start = System.currentTimeMillis()

                val result =
                    musicBrainzWebClient
                        .get()
                        .uri(fullUrl)
                        .retrieve()
                        .bodyToMono(clazz)
                        .awaitSingle()

                logger.info(
                    "MusicBrainz response OK | path={} | mbid={} | took={}ms",
                    path,
                    mbid,
                    System.currentTimeMillis() - start,
                )
                return result
            } catch (e: CancellationException) {
                throw e
            } catch (e: WebClientResponseException) {
                val code = e.statusCode.value()
                val body = e.responseBodyAsString.take(500)

                val ex: MusicBrainzClientException =
                    when (code) {
                        404 -> MusicBrainzClientException.NotFound("MusicBrainz 404", e, mbid, path)
                        in RETRYABLE_HTTP ->
                            MusicBrainzClientException.Retryable(
                                "MusicBrainz HTTP $code",
                                e,
                                mbid,
                                path,
                            )

                        else -> MusicBrainzClientException.Fatal("MusicBrainz HTTP $code body=$body", e, mbid, path)
                    }

                logger.warn(
                    "MusicBrainz HTTP error | status={} | path={} | mbid={} | body={}",
                    code,
                    path,
                    mbid,
                    body,
                )

                if (ex is MusicBrainzClientException.Retryable && attempt < MAX_ATTEMPTS) {
                    val retryAfterSec = e.headers.getFirst("Retry-After")?.toLongOrNull()
                    val retryAfterMs = retryAfterSec?.times(1000)
                    val retryNumber = attempt
                    val backoff = maxOf(retryAfterMs ?: 0L, computeBackoffMs(retryNumber))

                    logger.warn(
                        "MusicBrainz retry scheduled | status={} | path={} | mbid={} | in={}ms",
                        code,
                        path,
                        mbid,
                        backoff,
                    )
                    delay(backoff)
                    continue
                }

                throw ex
            } catch (e: Exception) {
                // transporte: ClosedChannelException, SSL handshake abort, timeout etc
                logger.warn(
                    "MusicBrainz request failed | path={} | mbid={} | attempt={}/{}",
                    path,
                    mbid,
                    attempt,
                    MAX_ATTEMPTS,
                    e,
                )

                val ex = MusicBrainzClientException.Retryable("MusicBrainz transport error", e, mbid, path)

                if (attempt < MAX_ATTEMPTS) {
                    val retryNumber = attempt
                    val backoff = computeBackoffMs(retryNumber)
                    logger.warn(
                        "MusicBrainz retry scheduled | reason=TRANSPORT | path={} | mbid={} | in={}ms",
                        path,
                        mbid,
                        backoff,
                    )
                    delay(backoff)
                    continue
                }

                throw ex
            }
        }
    }

    suspend fun getAlbumGenreNamesByMbid(
        albumMbid: String,
        max: Int,
    ): List<String> {
        logger.info("MusicBrainz genre lookup started | mbid={}", albumMbid)

        // 1) RELEASE
        val release =
            mbGet(
                path = "/ws/2/release/$albumMbid",
                inc = "tags+release-groups",
                clazz = MbReleaseResponse::class.java,
                mbid = albumMbid,
            )

        val releaseTags =
            release.tags
                .orEmpty()
                .mapNotNull { it.name }
                .map { it.lowercase().trim() }
                .filter { it.isNotBlank() }
                .distinct()

        val rgId =
            release.releaseGroup
                ?.id
                ?.trim()
                .orEmpty()

        logger.info(
            "MusicBrainz release parsed | mbid={} | releaseTags={} | releaseGroupId={}",
            albumMbid,
            releaseTags.size,
            if (rgId.isBlank()) "<none>" else rgId,
        )

        // 2) RELEASE-GROUP
        if (rgId.isNotBlank()) {
            try {
                val rg =
                    mbGet(
                        path = "/ws/2/release-group/$rgId",
                        inc = "genres+tags",
                        clazz = MbReleaseGroupResponse::class.java,
                        mbid = rgId,
                    )

                val rgNames =
                    buildList {
                        rg.genres.orEmpty().forEach { it.name?.let(::add) }
                        rg.tags.orEmpty().forEach { it.name?.let(::add) }
                    }.map { it.lowercase().trim() }
                        .filter { it.isNotBlank() }
                        .distinct()

                logger.info("MusicBrainz release-group parsed | rgId={} | genres={}", rgId, rgNames)

                return (rgNames + releaseTags).distinct().take(max)
            } catch (e: MusicBrainzClientException.NotFound) {
                logger.info("MusicBrainz release-group not found | rgId={} | fallback=release-tags", rgId)
            }
        }

        val finalTags = releaseTags.take(max)
        logger.info("MusicBrainz genre lookup finished | mbid={} | finalTags={}", albumMbid, finalTags)
        return finalTags
    }
}
