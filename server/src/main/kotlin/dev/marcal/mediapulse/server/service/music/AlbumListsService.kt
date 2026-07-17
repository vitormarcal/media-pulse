package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.AlbumListCreateRequest
import dev.marcal.mediapulse.server.api.music.AlbumListDetailsResponse
import dev.marcal.mediapulse.server.api.music.AlbumListListenedRequest
import dev.marcal.mediapulse.server.api.music.AlbumListOrderRequest
import dev.marcal.mediapulse.server.api.music.AlbumListSummaryDto
import dev.marcal.mediapulse.server.api.music.AlbumListUpdateRequest
import dev.marcal.mediapulse.server.model.music.AlbumList
import dev.marcal.mediapulse.server.repository.AlbumListQueryRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumListItemCrudRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumListRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumRepository
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class AlbumListsService(
    private val listRepository: AlbumListRepository,
    private val albumRepository: AlbumRepository,
    private val itemRepository: AlbumListItemCrudRepository,
    private val queryRepository: AlbumListQueryRepository,
) {
    @Transactional(readOnly = true)
    fun listAll(): List<AlbumListSummaryDto> = queryRepository.listAll()

    @Transactional(readOnly = true)
    fun details(slug: String): AlbumListDetailsResponse = queryRepository.details(slug)

    @Transactional
    fun create(request: AlbumListCreateRequest): AlbumListDetailsResponse {
        val name = normalizeRequiredName(request.name)
        val normalizedName = name.lowercase()
        if (listRepository.findByNormalizedName(normalizedName) != null) {
            throw ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma lista com este nome")
        }
        val list =
            listRepository.save(
                AlbumList(
                    name = name,
                    normalizedName = normalizedName,
                    slug = uniqueSlug(name),
                    description = normalizeDescription(request.description),
                ),
            )
        return queryRepository.details(list.slug)
    }

    @Transactional
    fun update(
        listId: Long,
        request: AlbumListUpdateRequest,
    ): AlbumListDetailsResponse {
        val list = requireList(listId)
        val name = normalizeRequiredName(request.name)
        val normalizedName = name.lowercase()
        listRepository.findByNormalizedName(normalizedName)?.let {
            if (it.id != listId) throw ResponseStatusException(HttpStatus.CONFLICT, "Já existe uma lista com este nome")
        }
        val updated =
            listRepository.save(
                list.copy(
                    name = name,
                    normalizedName = normalizedName,
                    description = normalizeDescription(request.description),
                    updatedAt = Instant.now(),
                ),
            )
        return queryRepository.details(updated.slug)
    }

    @Transactional
    fun delete(listId: Long) {
        listRepository.delete(requireList(listId))
    }

    @Transactional
    fun addAlbum(
        listId: Long,
        albumId: Long,
    ): AlbumListDetailsResponse {
        val list = requireList(listId)
        if (!albumRepository.existsById(albumId)) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Album not found")
        }
        itemRepository.add(listId, albumId)
        touch(list)
        return queryRepository.details(list.slug)
    }

    @Transactional
    fun removeAlbum(
        listId: Long,
        albumId: Long,
    ): AlbumListDetailsResponse {
        val list = requireList(listId)
        if (itemRepository.remove(listId, albumId) == 0) {
            throw ResponseStatusException(HttpStatus.NOT_FOUND, "Album list item not found")
        }
        normalizePositions(listId)
        touch(list)
        return queryRepository.details(list.slug)
    }

    @Transactional
    fun updateOrder(
        listId: Long,
        request: AlbumListOrderRequest,
    ): AlbumListDetailsResponse {
        val list = requireList(listId)
        val currentIds = queryRepository.itemIds(listId)
        if (request.albumIds.size != currentIds.size || request.albumIds.toSet() != currentIds.toSet()) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "albumIds inválidos para esta lista")
        }
        request.albumIds.forEachIndexed { index, albumId -> itemRepository.setPosition(listId, albumId, index + 1) }
        touch(list)
        return queryRepository.details(list.slug)
    }

    @Transactional
    fun updateListened(
        listId: Long,
        albumId: Long,
        request: AlbumListListenedRequest,
    ): AlbumListDetailsResponse {
        val list = requireList(listId)
        val updated = itemRepository.setListenedAt(listId, albumId, if (request.listened) Instant.now() else null)
        if (updated == 0) throw ResponseStatusException(HttpStatus.NOT_FOUND, "Album list item not found")
        touch(list)
        return queryRepository.details(list.slug)
    }

    private fun normalizePositions(listId: Long) {
        queryRepository.itemIds(listId).forEachIndexed { index, albumId ->
            itemRepository.setPosition(listId, albumId, index + 1)
        }
    }

    private fun requireList(listId: Long): AlbumList =
        listRepository.findById(listId).orElseThrow {
            ResponseStatusException(HttpStatus.NOT_FOUND, "Album list not found")
        }

    private fun touch(list: AlbumList) {
        listRepository.save(list.copy(updatedAt = Instant.now()))
    }

    private fun normalizeRequiredName(value: String): String =
        value.trim().replace("\\s+".toRegex(), " ").also {
            if (it.isBlank()) throw ResponseStatusException(HttpStatus.BAD_REQUEST, "name é obrigatório")
        }

    private fun normalizeDescription(value: String?): String? = value?.trim()?.replace("\\s+".toRegex(), " ")?.ifBlank { null }

    private fun uniqueSlug(name: String): String {
        val base = SlugTextUtil.normalize(name, maxLength = 72)
        var candidate = base
        var suffix = 2
        while (listRepository.findBySlug(candidate) != null) {
            candidate = "$base-$suffix"
            suffix += 1
        }
        return candidate
    }
}
