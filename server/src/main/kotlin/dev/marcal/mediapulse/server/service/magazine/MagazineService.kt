package dev.marcal.mediapulse.server.service.magazine

import dev.marcal.mediapulse.server.api.magazine.MagazineDetailsDto
import dev.marcal.mediapulse.server.api.magazine.MagazineIssueDto
import dev.marcal.mediapulse.server.api.magazine.MagazineIssueRequest
import dev.marcal.mediapulse.server.api.magazine.MagazineLibraryDto
import dev.marcal.mediapulse.server.api.magazine.MagazineOverviewDto
import dev.marcal.mediapulse.server.api.magazine.MagazinePublicationDto
import dev.marcal.mediapulse.server.api.magazine.MagazinePublicationRequest
import dev.marcal.mediapulse.server.api.magazine.MagazineReadDto
import dev.marcal.mediapulse.server.api.magazine.MagazineReadRequest
import dev.marcal.mediapulse.server.model.EntityType
import dev.marcal.mediapulse.server.model.magazine.MagazineIssue
import dev.marcal.mediapulse.server.model.magazine.MagazinePublication
import dev.marcal.mediapulse.server.model.magazine.MagazineRead
import dev.marcal.mediapulse.server.model.magazine.MagazineReadStatus
import dev.marcal.mediapulse.server.repository.MediaCommentQueryRepository
import dev.marcal.mediapulse.server.repository.crud.MagazineIssueRepository
import dev.marcal.mediapulse.server.repository.crud.MagazinePublicationRepository
import dev.marcal.mediapulse.server.repository.crud.MagazineReadRepository
import dev.marcal.mediapulse.server.repository.crud.MediaCommentRepository
import dev.marcal.mediapulse.server.repository.query.MagazineQueryRepository
import dev.marcal.mediapulse.server.util.TxUtil
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.multipart.MultipartFile
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

@Service
class MagazineService(
    private val publications: MagazinePublicationRepository,
    private val issues: MagazineIssueRepository,
    private val reads: MagazineReadRepository,
    private val query: MagazineQueryRepository,
    private val comments: MediaCommentQueryRepository,
    private val commentRepository: MediaCommentRepository,
    private val covers: MagazineCoverService,
    private val tx: TxUtil,
) {
    fun publications(): List<MagazinePublicationDto> = publications.findAllByOrderByNameAsc().map { it.toDto() }

    fun library(
        q: String?,
        publicationId: Long?,
        status: MagazineReadStatus?,
        page: Int,
        limit: Int,
    ): MagazineLibraryDto {
        if (page < 0 || limit !in 1..100) bad("Paginação inválida.")
        val ids = query.issueIds(q, publicationId, status, page, limit)
        return MagazineLibraryDto(cards(ids.take(limit)), if (ids.size > limit) page + 1 else null)
    }

    fun overview(): MagazineOverviewDto =
        MagazineOverviewDto(
            library(null, null, MagazineReadStatus.CURRENTLY_READING, 0, 6).items,
            library(null, null, MagazineReadStatus.READ, 0, 6).items,
            issues.count(),
            reads.countByStatus(MagazineReadStatus.READ),
        )

    fun details(id: Long): MagazineDetailsDto {
        val issue = issues.findById(id).orElseThrow { missing() }
        val journeys = reads.findByIssueIdOrderByIdDesc(id)
        return MagazineDetailsDto(
            card(issue, publication(issue.publicationId), latestRead(journeys)),
            journeys.map { it.toDto() },
            comments.findByEntity(EntityType.MAGAZINE_ISSUE, id),
        )
    }

    fun create(
        request: MagazineIssueRequest,
        cover: MultipartFile?,
    ): MagazineDetailsDto =
        withCover(cover) { url ->
            val publication = resolvePublication(request)
            val normalized = normalizeIssue(request, publication.id)
            val existing = issues.findByPublicationIdAndIdentityKey(publication.id, normalized.identityKey)
            if (existing != null) conflict("Este número já existe. Abra o detalhe para atualizar ou reler.")
            val issue = issues.saveAndFlush(normalized.copy(coverUrl = url))
            val read = request.read ?: bad("Escolha um estado para a leitura.")
            saveRead(issue, null, read)
            details(issue.id)
        }

    fun updateIssue(
        id: Long,
        request: MagazineIssueRequest,
        cover: MultipartFile?,
    ): MagazineDetailsDto {
        var oldCover: String? = null
        val result =
            withCover(cover) { url ->
                val current = lockedIssue(id)
                val publication = resolvePublication(request)
                val normalized = normalizeIssue(request, publication.id)
                val duplicate = issues.findByPublicationIdAndIdentityKey(publication.id, normalized.identityKey)
                if (duplicate != null && duplicate.id != id) conflict("Este número já existe.")
                val journeys = reads.findByIssueIdOrderByIdDesc(id)
                val totalPages = normalized.totalPages
                if (totalPages != null &&
                    journeys.any { it.status != MagazineReadStatus.READ && (it.currentPage ?: 0) > totalPages }
                ) {
                    bad("O total é menor que uma página já registrada. Corrija a leitura primeiro.")
                }
                val replacement =
                    if (url != null) {
                        url
                    } else if (request.removeCover) {
                        null
                    } else {
                        current.coverUrl
                    }
                oldCover = current.coverUrl?.takeIf { it != replacement }
                val updated =
                    current.copy(
                        publicationId = publication.id,
                        number = normalized.number,
                        coverDate = normalized.coverDate,
                        identityKey = normalized.identityKey,
                        totalPages = normalized.totalPages,
                        coverUrl = replacement,
                    )
                issues.saveAndFlush(updated)
                journeys.forEach { journey ->
                    val updatedRead =
                        when {
                            journey.status == MagazineReadStatus.READ -> journey.copy(currentPage = totalPages)
                            totalPages == null -> journey.copy(currentPage = null)
                            journey.currentPage != null -> journey.copy(progressPct = journey.currentPage!!.toDouble() / totalPages * 100)
                            else -> journey
                        }
                    reads.save(updatedRead)
                }
                details(id)
            }
        oldCover?.let { runCatching { covers.delete(it) } }
        return result
    }

    fun updatePublication(
        id: Long,
        request: MagazinePublicationRequest,
    ): MagazinePublicationDto =
        mutate {
            val current = publication(id)
            val normalized = normalizePublication(request)
            if (publications.findByNormalizedName(normalized.normalizedName)?.id?.let { it != id } == true ||
                normalized.issn?.let { publications.findByIssn(it)?.id?.let { other -> other != id } } == true
            ) {
                conflict("Esta publicação já existe.")
            }
            publications
                .saveAndFlush(
                    current.copy(name = normalized.name, normalizedName = normalized.normalizedName, issn = normalized.issn),
                ).toDto()
        }

    fun createRead(
        issueId: Long,
        request: MagazineReadRequest,
    ): MagazineDetailsDto =
        mutate {
            val issue = lockedIssue(issueId)
            saveRead(issue, null, request)
            details(issueId)
        }

    fun updateRead(
        issueId: Long,
        readId: Long,
        request: MagazineReadRequest,
    ): MagazineDetailsDto =
        mutate {
            val issue = lockedIssue(issueId)
            val current = reads.findById(readId).orElseThrow { missing() }
            if (current.issueId != issueId) missing()
            saveRead(issue, current, request)
            details(issueId)
        }

    fun deleteRead(
        issueId: Long,
        readId: Long,
    ) = mutate {
        val issue = lockedIssue(issueId)
        val current = reads.findById(readId).orElseThrow { missing() }
        if (current.issueId != issueId) missing()
        reads.delete(current)
        reads.flush()
        updateActivity(issue)
    }

    fun deleteIssue(id: Long) {
        val deleted =
            mutate {
                val current = lockedIssue(id)
                commentRepository.deleteByEntityTypeAndEntityId(EntityType.MAGAZINE_ISSUE, id)
                issues.delete(current)
                issues.flush()
                current
            }
        deleted.coverUrl?.let { runCatching { covers.delete(it) } }
    }

    private fun saveRead(
        issue: MagazineIssue,
        current: MagazineRead?,
        request: MagazineReadRequest,
    ) {
        val normalized = normalizeRead(issue, current, request)
        val open = setOf(MagazineReadStatus.WANT_TO_READ, MagazineReadStatus.CURRENTLY_READING)
        if (normalized.status in open &&
            reads.findByIssueIdOrderByIdDesc(issue.id).any { it.id != current?.id && it.status in open }
        ) {
            conflict("Este número já tem uma jornada em aberto. Atualize a leitura existente.")
        }
        reads.saveAndFlush(normalized)
        updateActivity(issue)
    }

    internal fun normalizeRead(
        issue: MagazineIssue,
        current: MagazineRead?,
        request: MagazineReadRequest,
    ): MagazineRead {
        var started = request.startedAt ?: current?.startedAt
        var finished = request.finishedAt
        val status = request.status
        if (status == MagazineReadStatus.WANT_TO_READ) {
            if (request.startedAt != null ||
                finished != null ||
                request.currentPage != null ||
                (request.progressPct ?: 0.0) != 0.0
            ) {
                bad("Quero ler não aceita datas ou progresso de leitura.")
            }
            return (
                current ?: MagazineRead(
                    issueId = issue.id,
                    status = status,
                )
            ).copy(status = status, startedAt = null, finishedAt = null, currentPage = null, progressPct = 0.0)
        }
        if (status == MagazineReadStatus.READ) {
            finished = finished ?: current?.finishedAt ?: bad("Informe a data de término.")
            started = started ?: finished
        } else {
            if (finished != null) bad("A data de término se aplica à leitura concluída.")
            if (started == null) bad("Informe a data de início.")
        }
        if (finished != null && finished < started) bad("O término não pode anteceder o início.")
        val percentage = request.progressPct
        if (percentage != null && (!percentage.isFinite() || percentage !in 0.0..100.0)) bad("Informe progresso entre 0 e 100%.")
        if (percentage != null && request.currentPage != null) bad("Informe porcentagem ou página, não ambas.")
        val totalPages = issue.totalPages
        val page = request.currentPage ?: if (percentage == null) current?.currentPage else null
        if (page != null && (page < 0 || (totalPages != null && page > totalPages))) bad("Página atual inválida.")
        val progress =
            when {
                status == MagazineReadStatus.READ -> 100.0
                page != null && totalPages != null -> page.toDouble() / totalPages * 100
                request.currentPage != null && issue.totalPages == null -> bad("Informe o total de páginas ou use porcentagem.")
                else -> percentage ?: current?.progressPct ?: 0.0
            }
        return (current ?: MagazineRead(issueId = issue.id, status = status)).copy(
            status = status,
            startedAt = started,
            finishedAt = finished,
            progressPct = progress,
            currentPage =
                if (status ==
                    MagazineReadStatus.READ
                ) {
                    issue.totalPages
                } else {
                    page
                },
        )
    }

    private fun updateActivity(issue: MagazineIssue) {
        val date =
            reads.findByIssueIdOrderByIdDesc(issue.id).maxOfOrNull {
                it.finishedAt ?: it.startedAt
                    ?: it.createdAt.atZone(ZoneOffset.UTC).toLocalDate()
            }
                ?: LocalDate.now()
        issues.saveAndFlush(issue.copy(activityDate = date))
    }

    private fun normalizeIssue(
        request: MagazineIssueRequest,
        publicationId: Long,
    ): MagazineIssue {
        val number = request.number?.trim()?.ifBlank { null }
        val date = request.coverDate?.trim()?.ifBlank { null }
        if (number == null && date == null) bad("Informe o número ou mês/ano da revista.")
        if ((number?.length ?: 0) > 100) bad("O número deve ter até 100 caracteres.")
        if (date != null) {
            if (!Regex("[0-9]{4}-(0[1-9]|1[0-2])").matches(date)) bad("Informe mês/ano no formato AAAA-MM.")
            try {
                YearMonth.parse(date)
            } catch (_: Exception) {
                bad("Mês/ano inválido.")
            }
        }
        if (request.totalPages != null && request.totalPages <= 0) bad("O total de páginas deve ser positivo.")
        // Number is the primary identity when present; month/year identifies unnumbered issues.
        val key = number?.let { "n:${it.lowercase()}" } ?: "d:$date"
        return MagazineIssue(
            publicationId = publicationId,
            number = number,
            coverDate = date,
            identityKey = key,
            totalPages = request.totalPages,
        )
    }

    private fun normalizePublication(request: MagazinePublicationRequest): MagazinePublication {
        val name = request.name.trim().replace(Regex("\\s+"), " ")
        if (name.isBlank() || name.length > 200) bad("Informe o nome da publicação, com até 200 caracteres.")
        val raw =
            request.issn
                ?.trim()
                ?.uppercase()
                ?.replace("-", "")
                ?.ifBlank { null }
        if (raw != null) {
            if (!Regex("[0-9]{7}[0-9X]").matches(raw)) bad("ISSN inválido.")
            val checksum =
                raw.take(7).mapIndexed { index, c -> c.digitToInt() * (8 - index) }.sum() +
                    if (raw.last() == 'X') 10 else raw.last().digitToInt()
            if (checksum % 11 != 0) bad("ISSN inválido.")
        }
        return MagazinePublication(name = name, normalizedName = name.lowercase(), issn = raw?.let { it.take(4) + "-" + it.drop(4) })
    }

    private fun resolvePublication(request: MagazineIssueRequest): MagazinePublication {
        if (request.publicationId != null) {
            if (request.publication != null) bad("Escolha uma publicação existente ou informe uma nova.")
            return publication(request.publicationId)
        }
        val normalized = normalizePublication(request.publication ?: bad("Informe a publicação."))
        val existing = publications.findByNormalizedName(normalized.normalizedName) ?: normalized.issn?.let(publications::findByIssn)
        if (existing != null) {
            if (normalized.issn != null &&
                existing.issn != null &&
                existing.issn != normalized.issn
            ) {
                conflict("O nome já está associado a outro ISSN.")
            }
            return if (existing.issn == null &&
                normalized.issn != null
            ) {
                publications.saveAndFlush(existing.copy(issn = normalized.issn))
            } else {
                existing
            }
        }
        return publications.saveAndFlush(normalized)
    }

    private fun cards(ids: List<Long>): List<MagazineIssueDto> {
        if (ids.isEmpty()) return emptyList()
        val byId = issues.findAllById(ids).associateBy { it.id }
        val publicationMap = publications.findAllById(byId.values.map { it.publicationId }.toSet()).associateBy { it.id }
        val latest = reads.findByIssueIdInOrderByIdDesc(ids).groupBy { it.issueId }.mapValues { latestRead(it.value) }
        return ids.mapNotNull { id -> byId[id]?.let { card(it, publicationMap.getValue(it.publicationId), latest[id]) } }
    }

    private fun card(
        issue: MagazineIssue,
        publication: MagazinePublication,
        read: MagazineRead?,
    ) = MagazineIssueDto(
        issue.id,
        publication.toDto(),
        issue.number,
        issue.coverDate,
        issue.totalPages,
        issue.coverUrl,
        issue.activityDate,
        read?.toDto(),
    )

    private fun latestRead(journeys: List<MagazineRead>): MagazineRead? =
        journeys.firstOrNull { it.status in setOf(MagazineReadStatus.WANT_TO_READ, MagazineReadStatus.CURRENTLY_READING) }
            ?: journeys.firstOrNull()

    private fun MagazinePublication.toDto() = MagazinePublicationDto(id, name, issn)

    private fun MagazineRead.toDto() = MagazineReadDto(id, issueId, status, startedAt, finishedAt, progressPct, currentPage, createdAt)

    private fun publication(id: Long) = publications.findById(id).orElseThrow { missing() }

    private fun lockedIssue(id: Long) = issues.lockById(id) ?: missing()

    private fun bad(message: String): Nothing = throw ResponseStatusException(HttpStatus.BAD_REQUEST, message)

    private fun missing(): Nothing = throw ResponseStatusException(HttpStatus.NOT_FOUND, "Registro de revista não encontrado.")

    private fun conflict(message: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, message)

    private fun <T> mutate(action: () -> T): T =
        try {
            tx.inTx(action)
        } catch (_: DataIntegrityViolationException) {
            conflict("O registro conflita com dados existentes. Recarregue e revise os campos.")
        }

    private fun <T> withCover(
        cover: MultipartFile?,
        action: (String?) -> T,
    ): T {
        val url = cover?.let(covers::save)
        try {
            return mutate { action(url) }
        } catch (error: Exception) {
            if (url != null) runCatching { covers.delete(url) }.exceptionOrNull()?.let(error::addSuppressed)
            throw error
        }
    }
}
