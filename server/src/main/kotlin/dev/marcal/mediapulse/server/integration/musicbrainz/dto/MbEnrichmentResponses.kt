package dev.marcal.mediapulse.server.integration.musicbrainz.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class MbReleaseGroupSearchResponse(
    @JsonProperty("release-groups") val releaseGroups: List<MbReleaseGroupCandidate> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MbReleaseGroupCandidate(
    val id: String,
    val title: String,
    @JsonProperty("first-release-date") val firstReleaseDate: String? = null,
    @JsonProperty("primary-type") val primaryType: String? = null,
    val disambiguation: String? = null,
    @JsonProperty("artist-credit") val artistCredit: List<MbArtistCredit> = emptyList(),
    val genres: List<MbReleaseGroupResponse.MbGenre> = emptyList(),
    val tags: List<MbReleaseGroupResponse.MbTag> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MbArtistCredit(
    val name: String? = null,
    val artist: MbArtistRef? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MbArtistRef(
    val id: String,
    val name: String,
    val disambiguation: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MbArtistSearchResponse(
    val artists: List<MbArtistCandidate> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MbArtistCandidate(
    val id: String,
    val name: String,
    val disambiguation: String? = null,
    val country: String? = null,
)
