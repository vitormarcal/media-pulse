package dev.marcal.mediapulse.server.fixture

import dev.marcal.mediapulse.server.model.EventSource
import org.apache.commons.codec.digest.DigestUtils

object EventSourceFixture {
    fun example(
        example: Int = 1,
        status: EventSource.Status = EventSource.Status.PENDING,
    ) = EventSource(
        id = 0L,
        provider = "plex",
        payload = PlexEventsFixture.musicEventsJson[example],
        status = status,
        fingerprint = DigestUtils.sha256Hex(PlexEventsFixture.musicEventsJson[example]),
    )
}
