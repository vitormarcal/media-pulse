package dev.marcal.mediapulse.server.integration.musicbrainz

sealed class MusicBrainzClientException(
    message: String,
    cause: Throwable? = null,
    val mbid: String? = null,
    val endpoint: String? = null,
) : RuntimeException(message, cause) {
    class Retryable(
        message: String,
        cause: Throwable? = null,
        mbid: String? = null,
        endpoint: String? = null,
        val retryAfterMs: Long? = null,
    ) : MusicBrainzClientException(message, cause, mbid, endpoint)

    class NotFound(
        message: String,
        cause: Throwable? = null,
        mbid: String? = null,
        endpoint: String? = null,
    ) : MusicBrainzClientException(message, cause, mbid, endpoint)

    class Fatal(
        message: String,
        cause: Throwable? = null,
        mbid: String? = null,
        endpoint: String? = null,
    ) : MusicBrainzClientException(message, cause, mbid, endpoint)
}
