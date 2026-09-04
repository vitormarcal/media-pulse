package dev.marcal.mediapulse.server.service.tv

import dev.marcal.mediapulse.server.api.shows.ShowListAttachRequest
import dev.marcal.mediapulse.server.api.shows.ShowListCoverUpdateRequest
import dev.marcal.mediapulse.server.api.shows.ShowListCreateRequest
import dev.marcal.mediapulse.server.api.shows.ShowListOrderUpdateRequest
import dev.marcal.mediapulse.server.api.shows.ShowListSummaryDto
import dev.marcal.mediapulse.server.model.tv.ShowList
import dev.marcal.mediapulse.server.repository.ShowListQueryRepository
import dev.marcal.mediapulse.server.repository.crud.ShowListItemCrudRepository
import dev.marcal.mediapulse.server.repository.crud.ShowListRepository
import dev.marcal.mediapulse.server.repository.crud.TvShowRepository
import dev.marcal.mediapulse.server.util.SlugTextUtil
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.server.ResponseStatusException
import java.time.Instant

@Service
class ShowListsService(
    private val shows: TvShowRepository,
    private val lists: ShowListRepository,
    private val items: ShowListItemCrudRepository,
    private val query: ShowListQueryRepository,
) {
    @Transactional(readOnly = true)
    fun listAll() = query.listAll()

    @Transactional
    fun create(request: ShowListCreateRequest): ShowListSummaryDto = summary(createList(request.name, request.description).id)

    @Transactional
    fun attach(
        showId: Long,
        request: ShowListAttachRequest,
    ): ShowListSummaryDto {
        requireShow(showId)
        val list =
            request.listId?.let { requireList(it) }
                ?: createList(request.name ?: throw badRequest("name é obrigatório"), request.description)
        items.upsert(list.id, showId)
        touch(list.id)
        return summary(list.id)
    }

    @Transactional
    fun remove(
        showId: Long,
        listId: Long,
    ) {
        requireShow(showId)
        val list = requireList(listId)
        if (items.remove(listId, showId) > 0) {
            lists.save(list.copy(coverShowId = list.coverShowId?.takeIf { it != showId }, updatedAt = Instant.now()))
        }
    }

    @Transactional
    fun updateOrder(
        listId: Long,
        request: ShowListOrderUpdateRequest,
    ) {
        requireList(listId)
        val current = items.positions(listId)
        if (request.showIds.size != current.size || request.showIds.toSet() != current.map { it.showId }.toSet()) {
            throw badRequest("showIds inválidos para esta lista")
        }
        val positions = current.associate { it.showId to it.position }
        request.showIds.forEachIndexed { index, showId ->
            if (positions[showId] != index + 1) items.updatePosition(listId, showId, index + 1)
        }
        touch(listId)
    }

    @Transactional
    fun updateCover(
        listId: Long,
        request: ShowListCoverUpdateRequest,
    ): ShowListSummaryDto {
        val list = requireList(listId)
        if (request.coverShowId != null && items.positions(listId).none { it.showId == request.coverShowId }) {
            throw badRequest("coverShowId inválido para esta lista")
        }
        lists.save(list.copy(coverShowId = request.coverShowId, updatedAt = Instant.now()))
        return summary(listId)
    }

    @Transactional
    fun delete(slug: String) {
        val list = lists.findBySlug(slug.trim()) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Show list not found")
        lists.delete(list)
    }

    private fun createList(
        name: String,
        description: String?,
    ): ShowList {
        val cleanName = name.trim().replace("\\s+".toRegex(), " ")
        if (cleanName.isBlank()) throw badRequest("name é obrigatório")
        val key = cleanName.lowercase()
        lists.findByNormalizedName(key)?.let { return it }
        val cleanDescription = description?.trim()?.replace("\\s+".toRegex(), " ")?.ifBlank { null }
        return lists.save(
            ShowList(
                name = cleanName,
                normalizedName = key,
                slug = SlugTextUtil.normalize(cleanName, 80),
                description = cleanDescription,
                updatedAt = Instant.now(),
            ),
        )
    }

    private fun requireShow(id: Long) = shows.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Show not found") }

    private fun requireList(id: Long) =
        lists.findById(id).orElseThrow { ResponseStatusException(HttpStatus.NOT_FOUND, "Show list not found") }

    private fun summary(id: Long) = query.summary(id) ?: throw ResponseStatusException(HttpStatus.NOT_FOUND, "Show list not found")

    private fun touch(id: Long) {
        val list = requireList(id)
        lists.save(list.copy(updatedAt = Instant.now()))
    }

    private fun badRequest(message: String) = ResponseStatusException(HttpStatus.BAD_REQUEST, message)
}
