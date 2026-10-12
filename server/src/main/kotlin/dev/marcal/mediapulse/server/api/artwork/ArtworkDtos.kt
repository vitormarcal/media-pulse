package dev.marcal.mediapulse.server.api.artwork

import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import dev.marcal.mediapulse.server.api.ratings.MediaRatingDto
import dev.marcal.mediapulse.server.model.artwork.Artwork
import dev.marcal.mediapulse.server.model.artwork.ArtworkArtist
import dev.marcal.mediapulse.server.model.artwork.ArtworkExperience
import dev.marcal.mediapulse.server.model.artwork.ArtworkExperienceType
import dev.marcal.mediapulse.server.model.artwork.ArtworkImage
import dev.marcal.mediapulse.server.model.artwork.ArtworkList
import java.time.LocalDate

data class ArtworkArtistRequest(
    val name: String,
    val biography: String? = null,
    val countryOfOrigin: String? = null,
    val bornAt: LocalDate? = null,
    val diedAt: LocalDate? = null,
)

data class ArtworkExperienceRequest(
    val experiencedAt: LocalDate? = null,
    val experienceType: ArtworkExperienceType? = null,
    val notes: String? = null,
    val sourceName: String? = null,
    val sourceUrl: String? = null,
)

data class ArtworkRequest(
    val title: String,
    val artistId: Long? = null,
    val artist: ArtworkArtistRequest? = null,
    val year: Int? = null,
    val type: String? = null,
    @JsonDeserialize(using = ArtworkMovementsDeserializer::class)
    val movements: List<String>? = null,
    // Legacy input is still accepted, but responses use movements.
    val movement: String? = null,
    val technique: String? = null,
    val description: String? = null,
    val experience: ArtworkExperienceRequest? = null,
)

data class ArtworkCardDto(
    val id: Long,
    val title: String,
    val artist: ArtworkArtist?,
    val year: Int?,
    val type: String?,
    val movements: List<String>,
    val favorite: Boolean,
    val imageUrl: String?,
)

data class ArtworkLibraryDto(
    val items: List<ArtworkCardDto>,
    val nextPage: Int?,
)

data class ArtworkDetailsDto(
    val artwork: Artwork,
    val artist: ArtworkArtist?,
    val experiences: List<ArtworkExperience>,
    val images: List<ArtworkImage>,
    val lists: List<ArtworkList>,
    val rating: MediaRatingDto?,
)

data class ArtworkListRequest(
    val name: String,
    val description: String? = null,
)

data class ArtworkListSummaryDto(
    val id: Long,
    val name: String,
    val description: String?,
    val count: Long,
)

data class ArtworkImageUrlRequest(
    val url: String,
    val caption: String? = null,
)

data class ArtworkFavoriteRequest(
    val favorite: Boolean,
)
