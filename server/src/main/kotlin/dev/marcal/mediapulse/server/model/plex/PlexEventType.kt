package dev.marcal.mediapulse.server.model.plex

enum class PlexEventType {
    PLAY,
    STOP,
    SCROBBLE,
    ;

    companion object {
        fun fromType(type: String): PlexEventType? =
            when (type) {
                "media.play" -> PLAY
                "media.stop" -> STOP
                "media.scrobble" -> SCROBBLE
                else -> null
            }
    }
}
