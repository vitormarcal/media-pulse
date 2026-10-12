package dev.marcal.mediapulse.server.model.artwork

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.Instant
import java.time.LocalDate

enum class ArtworkExperienceType { DIGITAL_VIEWING, IN_PERSON }

@Entity
@Table(name = "artwork_artists")
data class ArtworkArtist(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    val name: String,
    val biography: String? = null,
    @Column(name = "country_of_origin") val countryOfOrigin: String? = null,
    @Column(name = "born_at") val bornAt: LocalDate? = null,
    @Column(name = "died_at") val diedAt: LocalDate? = null,
    @Column(name = "portrait_url") val portraitUrl: String? = null,
)

@Entity
@Table(name = "artworks")
data class Artwork(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    val title: String,
    @Column(name = "artist_id") val artistId: Long? = null,
    val year: Int? = null,
    val type: String? = null,
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb") val movements: List<String> = emptyList(),
    @Column(name = "movements_revision") val movementsRevision: Long = 1,
    @Column(name = "movements_sync_pending") val movementsSyncPending: Boolean = false,
    @Column(name = "movements_sync_message") val movementsSyncMessage: String? = null,
    val technique: String? = null,
    val description: String? = null,
    val favorite: Boolean = false,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "artwork_experiences")
data class ArtworkExperience(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "artwork_id") val artworkId: Long,
    @Column(name = "experienced_at") val experiencedAt: LocalDate? = null,
    @Enumerated(EnumType.STRING) @Column(name = "experience_type") val experienceType: ArtworkExperienceType? = null,
    val notes: String? = null,
    @Column(name = "source_name") val sourceName: String? = null,
    @Column(name = "source_url") val sourceUrl: String? = null,
    @Column(name = "created_at") val createdAt: Instant = Instant.now(),
)

@Entity
@Table(name = "artwork_images")
data class ArtworkImage(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "artwork_id") val artworkId: Long,
    val url: String,
    @Column(name = "source_url") val sourceUrl: String? = null,
    val caption: String? = null,
    @Column(name = "is_primary") val primary: Boolean = false,
)

@Entity
@Table(name = "artwork_lists")
data class ArtworkList(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    val name: String,
    val description: String? = null,
)

@Entity
@Table(name = "artwork_list_items")
data class ArtworkListItem(
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY) val id: Long = 0,
    @Column(name = "list_id") val listId: Long,
    @Column(name = "artwork_id") val artworkId: Long,
)
