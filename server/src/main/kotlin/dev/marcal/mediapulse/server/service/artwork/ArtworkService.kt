package dev.marcal.mediapulse.server.service.artwork

import dev.marcal.mediapulse.server.api.artwork.ArtworkArtistRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkCardDto
import dev.marcal.mediapulse.server.api.artwork.ArtworkDetailsDto
import dev.marcal.mediapulse.server.api.artwork.ArtworkExperienceRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkLibraryDto
import dev.marcal.mediapulse.server.api.artwork.ArtworkListRequest
import dev.marcal.mediapulse.server.api.artwork.ArtworkListSummaryDto
import dev.marcal.mediapulse.server.api.artwork.ArtworkRequest
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.artwork.Artwork
import dev.marcal.mediapulse.server.model.artwork.ArtworkArtist
import dev.marcal.mediapulse.server.model.artwork.ArtworkExperience
import dev.marcal.mediapulse.server.model.artwork.ArtworkList
import dev.marcal.mediapulse.server.model.artwork.ArtworkListItem
import dev.marcal.mediapulse.server.repository.MediaRatingQueryRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkArtistRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkExperienceRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkImageRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkListItemRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkListRepository
import dev.marcal.mediapulse.server.repository.crud.ArtworkRepository
import dev.marcal.mediapulse.server.repository.crud.MediaRatingRepository
import dev.marcal.mediapulse.server.repository.query.ArtworkQueryRepository
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.net.URI

@Service
class ArtworkService(
    private val works: ArtworkRepository,
    private val artists: ArtworkArtistRepository,
    private val experiences: ArtworkExperienceRepository,
    private val images: ArtworkImageRepository,
    private val lists: ArtworkListRepository,
    private val items: ArtworkListItemRepository,
    private val query: ArtworkQueryRepository,
    private val ratings: MediaRatingQueryRepository,
    private val ratingRepository: MediaRatingRepository,
) {
    @Transactional(readOnly = true)
    fun library(
        q: String?,
        artistId: Long?,
        type: String?,
        movement: String?,
        favorite: Boolean?,
        listId: Long?,
        page: Int,
        limit: Int,
        sort: String = "recent",
    ): ArtworkLibraryDto {
        if (page < 0 || limit !in 1..100) bad("Paginação inválida.")
        if (sort !in setOf("recent", "year")) bad("Ordenação inválida.")
        val ids = query.ids(q, artistId, clean(type, 100), clean(movement, 200), favorite, listId, page, limit, sort == "year")
        val byId = works.findAllById(ids.take(limit)).associateBy { it.id }
        return ArtworkLibraryDto(ids.take(limit).map { card(byId.getValue(it)) }, if (ids.size > limit) page + 1 else null)
    }

    fun filters() = query.filters()

    fun artists(): List<ArtworkArtist> = artists.findAllByOrderByNameAscIdAsc()

    fun artist(id: Long): ArtworkArtist = artists.findById(id).orElseThrow { missing("Artista não encontrado.") }

    fun list(id: Long): ArtworkList = lists.findById(id).orElseThrow { missing("Lista não encontrada.") }

    fun lists(): List<ArtworkListSummaryDto> =
        lists.findAllByOrderByNameAscIdAsc().map {
            ArtworkListSummaryDto(it.id, it.name, it.description, items.countByListId(it.id))
        }

    fun work(id: Long): Artwork = works.findById(id).orElseThrow { missing("Obra não encontrada.") }

    @Transactional(readOnly = true)
    fun details(id: Long): ArtworkDetailsDto {
        val work = work(id)
        return ArtworkDetailsDto(
            work,
            work.artistId?.let(::artist),
            experiences.findByArtworkIdOrderByExperiencedAtDescIdDesc(id),
            images.findByArtworkIdOrderByIdAsc(id),
            lists
                .findAllById(
                    items.findByArtworkId(id).map {
                        it.listId
                    },
                ).sortedBy { it.name },
            ratings.findByEntity(EntityType.ARTWORK, id),
        )
    }

    @Transactional
    fun create(request: ArtworkRequest): ArtworkDetailsDto {
        val saved = works.save(toWork(request))
        request.experience?.let { experience ->
            normalizedExperience(saved.id, experience)?.let(experiences::save)
        }
        return details(saved.id)
    }

    @Transactional
    fun update(
        id: Long,
        request: ArtworkRequest,
    ): ArtworkDetailsDto {
        val current = work(id)
        if (request.experience != null) bad("Edite experiências pelo histórico da obra.")
        works.save(toWork(request).copy(id = id, favorite = current.favorite, createdAt = current.createdAt))
        return details(id)
    }

    private fun toWork(request: ArtworkRequest): Artwork {
        val title = required(request.title, 300, "Informe o título da obra.")
        if (request.artistId != null && request.artist != null) bad("Escolha um artista existente ou cadastre um novo.")
        val artistId = request.artistId?.also { artist(it) } ?: request.artist?.let { saveArtist(null, it).id }
        return Artwork(
            title = title,
            artistId = artistId,
            year = request.year,
            type = clean(request.type, 100),
            movement = clean(request.movement, 200),
            technique = clean(request.technique, 200),
            description = clean(request.description),
        )
    }

    @Transactional
    fun favorite(
        id: Long,
        favorite: Boolean,
    ): ArtworkDetailsDto {
        works.save(work(id).copy(favorite = favorite))
        return details(id)
    }

    @Transactional
    fun delete(id: Long) {
        work(id)
        ratingRepository.deleteByEntityTypeAndEntityId(EntityType.ARTWORK, id)
        works.deleteById(id)
    }

    @Transactional
    fun saveArtist(
        id: Long?,
        request: ArtworkArtistRequest,
    ): ArtworkArtist {
        val current = id?.let(::artist)
        if (request.bornAt != null &&
            request.diedAt != null &&
            request.diedAt < request.bornAt
        ) {
            bad("Falecimento não pode anteceder nascimento.")
        }
        return artists.save(
            ArtworkArtist(
                id = current?.id ?: 0,
                name = required(request.name, 200, "Informe o nome do artista."),
                biography = clean(request.biography),
                countryOfOrigin = clean(request.countryOfOrigin, 200),
                bornAt = request.bornAt,
                diedAt = request.diedAt,
                portraitUrl = current?.portraitUrl,
            ),
        )
    }

    @Transactional
    fun portrait(
        id: Long,
        url: String?,
    ): ArtworkArtist = artists.save(artist(id).copy(portraitUrl = url))

    @Transactional
    fun deleteArtist(
        id: Long,
        detach: Boolean,
    ) {
        artist(id)
        val linked = works.findByArtistId(id)
        if (linked.isNotEmpty() &&
            !detach
        ) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Desvincule explicitamente as obras antes de excluir o artista.")
        }
        linked.forEach { works.save(it.copy(artistId = null)) }
        artists.deleteById(id)
    }

    @Transactional
    fun saveExperience(
        id: Long,
        experienceId: Long?,
        request: ArtworkExperienceRequest,
    ): ArtworkExperience {
        work(id)
        val current = experienceId?.let { experiences.findById(it).orElseThrow { missing("Experiência não encontrada.") } }
        if (current != null && current.artworkId != id) missing("Experiência não encontrada.")
        val value = normalizedExperience(id, request) ?: bad("Informe ao menos um dado da experiência.")
        return experiences.save(value.copy(id = current?.id ?: 0, createdAt = current?.createdAt ?: value.createdAt))
    }

    private fun normalizedExperience(
        id: Long,
        request: ArtworkExperienceRequest,
    ): ArtworkExperience? {
        val value =
            ArtworkExperience(
                artworkId = id,
                experiencedAt = request.experiencedAt,
                experienceType = request.experienceType,
                notes = clean(request.notes),
                sourceName = clean(request.sourceName, 300),
                sourceUrl = httpUrl(request.sourceUrl),
            )
        return value.takeIf {
            it.experiencedAt != null ||
                it.experienceType != null ||
                it.notes != null ||
                it.sourceName != null ||
                it.sourceUrl != null
        }
    }

    @Transactional
    fun deleteExperience(
        id: Long,
        experienceId: Long,
    ) {
        work(id)
        val experience = experiences.findById(experienceId).orElseThrow { missing("Experiência não encontrada.") }
        if (experience.artworkId != id) missing("Experiência não encontrada.")
        experiences.delete(experience)
    }

    @Transactional
    fun saveList(
        id: Long?,
        request: ArtworkListRequest,
    ): ArtworkList {
        id?.let(::list)
        return lists.save(
            ArtworkList(
                id = id ?: 0,
                name = required(request.name, 200, "Informe o nome da lista."),
                description = clean(request.description),
            ),
        )
    }

    @Transactional
    fun deleteList(id: Long) {
        list(id)
        lists.deleteById(id)
    }

    @Transactional
    fun addToList(
        id: Long,
        listId: Long,
    ) {
        work(id)
        list(listId)
        if (items.findByListIdAndArtworkId(listId, id) == null) items.save(ArtworkListItem(listId = listId, artworkId = id))
    }

    @Transactional
    fun removeFromList(
        id: Long,
        listId: Long,
    ) {
        work(id)
        list(listId)
        items.findByListIdAndArtworkId(listId, id)?.let(items::delete)
    }

    private fun card(work: Artwork): ArtworkCardDto =
        ArtworkCardDto(
            work.id,
            work.title,
            work.artistId?.let(::artist),
            work.year,
            work.type,
            work.movement,
            work.favorite,
            images.findByArtworkIdOrderByIdAsc(work.id).let { gallery -> (gallery.find { it.primary } ?: gallery.firstOrNull())?.url },
        )

    private fun required(
        value: String,
        max: Int,
        message: String,
    ): String = clean(value, max) ?: bad(message)

    private fun clean(
        value: String?,
        max: Int = 20_000,
    ): String? {
        val result = value?.trim()?.ifBlank { null }
        if (result != null && result.length > max) bad("Texto excede o limite de $max caracteres.")
        return result
    }

    private fun httpUrl(value: String?): String? {
        val result = clean(value, 4000) ?: return null
        val uri =
            try {
                URI(result)
            } catch (_: Exception) {
                bad("Informe um link HTTP ou HTTPS válido.")
            }
        if (uri.scheme?.lowercase() !in setOf("http", "https") ||
            uri.host == null ||
            uri.userInfo != null
        ) {
            bad("Informe um link HTTP ou HTTPS válido.")
        }
        return result
    }

    private fun bad(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private fun missing(message: String): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, message)
}
