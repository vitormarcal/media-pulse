package dev.marcal.mediapulse.server.model

enum class SourceIdentifier(
    val tag: String,
    val description: String,
) {
    MUSICBRAINZ(tag = "mbid", description = "MusicBrainz identifier"),
    ;

    companion object {
        fun fromTag(tag: String): SourceIdentifier? = SourceIdentifier.entries.firstOrNull { it.tag == tag }
    }
}
