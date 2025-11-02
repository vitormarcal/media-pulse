package dev.marcal.mediapulse.server.model.plex

enum class PlexEventType(
    val type: String,
) {
    PLAY("media.play"),
    STOP("media.stop"),
    SCROBBLE("media.scrobble"),
    ;

    companion object {
        fun fromType(type: String): PlexEventType? = PlexEventType.entries.firstOrNull { it.type == type }
    }
}
