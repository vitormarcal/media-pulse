package dev.marcal.mediapulse.server.api.music

data class WikipediaIntroductionDto(
    val text: String,
    val language: String,
    val sourceUrl: String,
    val revisionId: Long? = null,
    val license: String,
    val licenseUrl: String,
)
