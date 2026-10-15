package dev.marcal.mediapulse.server.service.music

import dev.marcal.mediapulse.server.api.music.AlbumArtistDto
import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillArtist
import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillBatch
import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillItem
import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillPreviewRequest
import dev.marcal.mediapulse.server.api.music.AlbumCreditBackfillStatus
import dev.marcal.mediapulse.server.api.music.MusicBrainzAlbumArtistDto
import dev.marcal.mediapulse.server.integration.musicbrainz.MusicBrainzApiClient
import dev.marcal.mediapulse.server.integration.spotify.SpotifyApiClient
import dev.marcal.mediapulse.server.integration.spotify.dto.SpotifyArtist
import dev.marcal.mediapulse.server.model.music.AlbumArtistCredit
import dev.marcal.mediapulse.server.model.music.AlbumArtistSource
import dev.marcal.mediapulse.server.repository.crud.AlbumArtistRepository
import dev.marcal.mediapulse.server.repository.crud.AlbumCreditBackfillRepository
import dev.marcal.mediapulse.server.repository.crud.ArtistRepository
import dev.marcal.mediapulse.server.repository.query.AlbumArtistQueryRepository
import dev.marcal.mediapulse.server.util.FingerprintUtil
import dev.marcal.mediapulse.server.util.TxUtil
import kotlinx.coroutines.CancellationException
import org.springframework.http.HttpStatus
import org.springframework.stereotype.Service
import org.springframework.web.server.ResponseStatusException
import java.util.UUID

@Service
class AlbumCreditBackfillService(
    private val batches: AlbumCreditBackfillRepository,
    private val credits: AlbumArtistRepository,
    private val query: AlbumArtistQueryRepository,
    private val artists: ArtistRepository,
    private val spotify: SpotifyApiClient,
    private val musicBrainz: MusicBrainzApiClient,
    private val updates: AlbumArtistCreditsService,
    private val tx: TxUtil,
) {
    fun recent(): List<AlbumCreditBackfillBatch> = batches.recent()

    fun get(batchId: UUID): AlbumCreditBackfillBatch = batches.batch(batchId)

    suspend fun preview(request: AlbumCreditBackfillPreviewRequest): AlbumCreditBackfillBatch {
        if (request.limit !in 1..10 || request.albumIds.size > 10 || request.albumIds.any { it <= 0 }) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Use entre 1 e 10 álbuns por lote, com IDs positivos")
        }
        val targets = request.albumIds.distinct().ifEmpty { batches.targets(request.limit) }
        val capturedItems = targets.map { albumId -> tx.inTx { capture(albumId) } }
        val batchId = batches.create()
        tx.inTx {
            capturedItems.forEach { item ->
                batches.save(
                    batchId,
                    if (item.status ==
                        AlbumCreditBackfillStatus.READY
                    ) {
                        item.copy(status = AlbumCreditBackfillStatus.PENDING)
                    } else {
                        item
                    },
                )
            }
        }
        for (captured in capturedItems.filter { it.status == AlbumCreditBackfillStatus.READY }) {
            batches.save(batchId, fetchProposal(captured))
        }
        return get(batchId)
    }

    private fun capture(albumId: Long): AlbumCreditBackfillItem {
        if (batches.album(albumId) == null) {
            return AlbumCreditBackfillItem(albumId, "Álbum removido", AlbumCreditBackfillStatus.SKIPPED, "Álbum não encontrado")
        }
        credits.lockAlbum(albumId)
        val album = requireNotNull(batches.album(albumId))
        val before = query.findByAlbumIds(listOf(albumId))[albumId].orEmpty()
        val provider = if (album.releaseGroupId != null) AlbumArtistSource.MUSICBRAINZ_CONFIRMED else AlbumArtistSource.SPOTIFY
        val externalId = album.releaseGroupId ?: album.spotifyId
        val reason =
            when {
                before.any { it.source != AlbumArtistSource.LEGACY } -> "Os créditos já estão preenchidos; preservados"
                externalId == null -> "Sem ID Spotify ou release group MusicBrainz conhecido"
                else -> null
            }
        return AlbumCreditBackfillItem(
            albumId,
            album.title,
            if (reason == null) AlbumCreditBackfillStatus.READY else AlbumCreditBackfillStatus.SKIPPED,
            reason,
            before,
            credits.findByAlbumId(albumId).isNotEmpty(),
            album.revision,
            provider,
            externalId,
        )
    }

    private suspend fun fetchProposal(item: AlbumCreditBackfillItem): AlbumCreditBackfillItem =
        try {
            val externalId = requireNotNull(item.providerId)
            val proposed =
                if (item.provider == AlbumArtistSource.MUSICBRAINZ_CONFIRMED) {
                    val remote = musicBrainz.getReleaseGroup(externalId)
                    if (remote.id != externalId) conflict("O MusicBrainz retornou outra identidade")
                    remote.artistCredit.map { credit ->
                        val artist = credit.artist ?: conflict("Crédito incompleto no MusicBrainz")
                        AlbumCreditBackfillArtist(
                            artist.id,
                            artist.name,
                            credit.name?.takeIf(String::isNotBlank) ?: artist.name,
                            credit.joinPhrase,
                        )
                    }
                } else {
                    val remote = spotify.getAlbum(externalId)
                    if (remote.id != externalId || remote.name.isNullOrBlank()) conflict("Identidade de álbum inválida no Spotify")
                    remote.artists.orEmpty().mapIndexed { position, artist ->
                        AlbumCreditBackfillArtist(
                            artist.id.orEmpty(),
                            artist.name.orEmpty(),
                            artist.name.orEmpty(),
                            if (position < remote.artists.orEmpty().lastIndex) ", " else "",
                        )
                    }
                }
            if (proposed.isEmpty() || proposed.any { it.externalId.isBlank() || it.name.isBlank() || it.creditedName.isBlank() }) {
                conflict("O provedor não retornou uma lista completa de artistas")
            }
            identityProblem(requireNotNull(item.provider), proposed)?.let(::conflict)
            item.copy(proposed = proposed)
        } catch (e: CancellationException) {
            throw e
        } catch (e: ResponseStatusException) {
            item.copy(status = AlbumCreditBackfillStatus.CONFLICT, reason = e.reason)
        } catch (_: Exception) {
            item.copy(
                status = AlbumCreditBackfillStatus.ERROR,
                reason = "Não foi possível consultar o provedor; gere outra prévia para este álbum",
            )
        }

    // Read-only checks: preview must not create artists or bind identities.
    private fun identityProblem(
        provider: AlbumArtistSource,
        proposed: List<AlbumCreditBackfillArtist>,
    ): String? {
        if (proposed.groupBy { FingerprintUtil.artistFp(it.name) }.values.any { group ->
                group.map { it.externalId }.distinct().size > 1
            }
        ) {
            return "Artistas homônimos exigem revisão manual"
        }
        val localIds = mutableMapOf<Long, String>()
        for (artist in proposed) {
            val byId =
                if (provider == AlbumArtistSource.SPOTIFY) {
                    artists.findBySpotifyId(artist.externalId)
                } else {
                    artists.findByMusicbrainzArtistId(artist.externalId)
                }
            val fingerprint = FingerprintUtil.artistFp(artist.name)
            val local =
                byId ?: artists.findByFingerprint(fingerprint)
                    ?: if (provider == AlbumArtistSource.SPOTIFY) artists.findByNameAlias(fingerprint) else null
            val linkedId = if (provider == AlbumArtistSource.SPOTIFY) local?.spotifyId else local?.musicbrainzArtistId
            if (linkedId != null &&
                linkedId != artist.externalId
            ) {
                return "Nome local vinculado a outra identidade; revisão manual necessária"
            }
            if (local != null && localIds.put(local.id, artist.externalId)?.let { it != artist.externalId } == true) {
                return "Dois créditos resolvem para o mesmo artista local; revisão manual necessária"
            }
        }
        return null
    }

    fun apply(batchId: UUID): AlbumCreditBackfillBatch {
        val eligible = setOf(AlbumCreditBackfillStatus.READY, AlbumCreditBackfillStatus.ERROR)
        for (snapshot in get(batchId).items.filter { it.status in eligible && it.proposed.isNotEmpty() }) {
            try {
                tx.inTx {
                    val item = batches.lockItem(batchId, snapshot.albumId)
                    if (item.status in eligible) applyItem(batchId, item)
                }
            } catch (e: Exception) {
                recordFailure(
                    batchId,
                    snapshot.albumId,
                    eligible,
                    if (e is ResponseStatusException) {
                        AlbumCreditBackfillStatus.CONFLICT
                    } else {
                        AlbumCreditBackfillStatus.ERROR
                    },
                    if (e is ResponseStatusException) {
                        e.reason
                    } else {
                        "Falha ao aplicar; nenhuma alteração parcial foi mantida. Pode retomar o lote"
                    },
                )
            }
        }
        return get(batchId)
    }

    private fun applyItem(
        batchId: UUID,
        item: AlbumCreditBackfillItem,
    ) {
        lockExistingAlbum(item.albumId)
        if (credits.revision(item.albumId) != item.beforeRevision || !sameCredits(current(item.albumId), item.before)) {
            conflict("Créditos alterados depois da prévia; gere outra prévia")
        }
        val provider = requireNotNull(item.provider)
        if (!batches.isLinked(
                item.albumId,
                provider,
                requireNotNull(item.providerId),
            )
        ) {
            conflict("O vínculo do provedor mudou; gere outra prévia")
        }
        identityProblem(provider, item.proposed)?.let(::conflict)
        if (provider == AlbumArtistSource.MUSICBRAINZ_CONFIRMED) {
            updates.applyMusicBrainz(
                item.albumId,
                item.proposed.mapIndexed { position, artist ->
                    MusicBrainzAlbumArtistDto(artist.externalId, artist.name, artist.creditedName, artist.joinPhrase, position)
                },
            )
        } else {
            updates.applySpotify(item.albumId, item.proposed.map { SpotifyArtist(it.externalId, it.name) })
        }
        val after = current(item.albumId)
        if (sameCredits(after, item.before)) {
            batches.save(
                batchId,
                item.copy(
                    status = AlbumCreditBackfillStatus.SKIPPED,
                    reason = "A atualização foi preservada pelas regras de identidade ou precedência",
                ),
            )
        } else {
            batches.save(
                batchId,
                item.copy(
                    status = AlbumCreditBackfillStatus.APPLIED,
                    reason = null,
                    after = after,
                    afterRevision = credits.revision(item.albumId),
                ),
            )
        }
    }

    fun undo(batchId: UUID): AlbumCreditBackfillBatch {
        val eligible = setOf(AlbumCreditBackfillStatus.APPLIED, AlbumCreditBackfillStatus.UNDO_CONFLICT)
        for (snapshot in get(batchId).items.filter { it.status in eligible }) {
            try {
                tx.inTx {
                    val item = batches.lockItem(batchId, snapshot.albumId)
                    if (item.status in eligible) {
                        lockExistingAlbum(item.albumId)
                        if (!batches.isLinked(item.albumId, requireNotNull(item.provider), requireNotNull(item.providerId))) {
                            conflict("O vínculo do provedor mudou após o lote")
                        }
                        if (credits.revision(item.albumId) != item.afterRevision ||
                            !sameCredits(current(item.albumId), item.after.orEmpty())
                        ) {
                            conflict("O álbum foi editado após o lote; desfazimento recusado")
                        }
                        credits.replace(item.albumId, if (item.hadStoredCredits) stored(item.albumId, item.before) else emptyList())
                        batches.save(batchId, item.copy(status = AlbumCreditBackfillStatus.UNDONE, reason = null))
                    }
                }
            } catch (_: Exception) {
                recordFailure(
                    batchId,
                    snapshot.albumId,
                    eligible,
                    AlbumCreditBackfillStatus.UNDO_CONFLICT,
                    "Álbum removido ou alterado após o lote; nenhuma alteração foi desfeita",
                )
            }
        }
        return get(batchId)
    }

    private fun current(albumId: Long) = query.findByAlbumIds(listOf(albumId))[albumId].orEmpty()

    private fun stored(
        albumId: Long,
        values: List<AlbumArtistDto>,
    ) = values.map {
        AlbumArtistCredit(albumId, it.position, it.artistId, it.creditedName, it.joinPhrase, it.source)
    }

    private fun sameCredits(
        left: List<AlbumArtistDto>,
        right: List<AlbumArtistDto>,
    ) = stored(0, left) == stored(0, right)

    private fun lockExistingAlbum(albumId: Long) {
        if (batches.album(albumId) == null) conflict("Álbum removido; revisão manual necessária")
        credits.lockAlbum(albumId)
    }

    private fun recordFailure(
        batchId: UUID,
        albumId: Long,
        eligible: Set<AlbumCreditBackfillStatus>,
        status: AlbumCreditBackfillStatus,
        reason: String?,
    ) {
        tx.inTx {
            val current = batches.lockItem(batchId, albumId)
            if (current.status in eligible) batches.save(batchId, current.copy(status = status, reason = reason))
        }
    }

    private fun conflict(reason: String): Nothing = throw ResponseStatusException(HttpStatus.CONFLICT, reason)
}
