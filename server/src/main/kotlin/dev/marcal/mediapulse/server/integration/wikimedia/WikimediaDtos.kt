package dev.marcal.mediapulse.server.integration.wikimedia

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class WikidataClaimsResponse(
    val claims: Map<String, List<WikidataClaim>> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WikidataClaim(
    val mainsnak: WikidataSnak? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WikidataSnak(
    val datavalue: WikidataValue? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class WikidataValue(
    val value: String? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommonsQueryResponse(
    val query: CommonsQuery? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommonsQuery(
    val pages: Map<String, CommonsPage> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommonsPage(
    val title: String? = null,
    val imageinfo: List<CommonsImageInfo> = emptyList(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommonsImageInfo(
    val url: String? = null,
    val descriptionurl: String? = null,
    val thumburl: String? = null,
    val thumbmime: String? = null,
    val mime: String? = null,
    val size: Long? = null,
    val extmetadata: Map<String, CommonsMetadataValue> = emptyMap(),
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class CommonsMetadataValue(
    val value: String? = null,
)

data class WikimediaImageMetadata(
    val fileName: String,
    val downloadUrl: String,
    val originalUrl: String,
    val descriptionUrl: String?,
    val mimeType: String?,
    val author: String?,
    val license: String?,
    val licenseUrl: String?,
)
