package dev.marcal.mediapulse.server.fixture

import dev.marcal.mediapulse.server.model.EventSource
import org.apache.commons.codec.digest.DigestUtils

object EventSourceFixture {
    fun example() =
        EventSource(
            id = 0L,
            provider = "plex",
            payload = PlexEventsFixture.musicEventsJson[1],
            status = EventSource.Status.PENDING,
            fingerprint = DigestUtils.sha256Hex(PlexEventsFixture.musicEventsJson[1]),
        )
}
