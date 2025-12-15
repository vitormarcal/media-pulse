package dev.marcal.mediapulse.server.integration.musicbrainz.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class MbReleaseGroupResponse(
    val id: String? = null,
    val title: String? = null,
    val genres: List<MbGenre>? = null,
    val tags: List<MbTag>? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MbGenre(
        val name: String? = null,
        val count: Int? = null,
    )

    @JsonIgnoreProperties(ignoreUnknown = true)
    data class MbTag(
        val name: String? = null,
        val count: Int? = null,
    )
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class MbReleaseResponse(
    val id: String? = null,
    val title: String? = null,
    val tags: List<MbReleaseGroupResponse.MbTag>? = null,
    @JsonProperty("release-group")
    val releaseGroup: ReleaseGroupRef? = null,
) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    data class ReleaseGroupRef(
        val id: String? = null,
        val title: String? = null,
    )
}
