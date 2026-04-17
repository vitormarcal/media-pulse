<template>
  <main class="duplicate-review-page">
    <section class="admin-subheader">
      <div class="admin-subheader__copy">
        <p class="review-kicker">Painel administrativo</p>
        <h2>Ferramentas de saneamento da biblioteca</h2>
        <p class="admin-subheader__summary">
          Área operacional para revisar inconsistências de catálogo, consolidar faixas e limpar duplicações sem sair da linguagem visual principal.
        </p>
      </div>

      <nav class="admin-subheader__nav" aria-label="Navegação administrativa de música">
        <NuxtLink class="admin-nav-link" to="/music">
          Música
        </NuxtLink>
        <NuxtLink class="admin-nav-link" to="/music/library">
          Biblioteca
        </NuxtLink>
        <span class="admin-nav-link admin-nav-link--active">
          Revisão de duplicatas
        </span>
      </nav>
    </section>

    <section class="review-hero">
      <div class="review-hero__copy">
        <p class="review-kicker">Administração de música</p>
        <h1>Revisão de faixas duplicadas</h1>
        <p class="review-intro">
          Esta fila reúne grupos suspeitos dentro do mesmo álbum. Você escolhe a faixa canônica,
          marca o que deve ser absorvido e executa o merge com visibilidade do que será preservado.
        </p>
      </div>

      <div class="review-hero__actions">
        <NuxtLink class="secondary-link" to="/music/library">
          Voltar para a biblioteca
        </NuxtLink>

        <label class="toggle-card">
          <input v-model="includeIgnored" type="checkbox">
          <span>Mostrar grupos ignorados</span>
        </label>
      </div>
    </section>

    <div v-if="showInitialLoading" class="state-card">
      <p>Carregando grupos suspeitos...</p>
    </div>

    <div v-else-if="error && !groups.length" class="state-card state-card--error">
      <p>Não foi possível carregar a revisão de duplicatas.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else>
      <section class="review-filters">
        <label class="filter-field">
          <span>Filtrar por artista</span>
          <input
            v-model="artistFilterInput"
            type="search"
            placeholder="Ex.: Milton Nascimento"
          >
        </label>

        <label class="filter-field">
          <span>Filtrar por álbum</span>
          <input
            v-model="albumFilterInput"
            type="search"
            placeholder="Ex.: Clube da Esquina"
          >
        </label>

        <p class="filter-status" :data-pending="isRefreshingFilters">
          {{ filterStatusMessage }}
        </p>
      </section>

      <section class="review-toolbar">
        <div class="toolbar-copy">
          <p class="summary-label">Recorte ativo</p>
          <p class="toolbar-summary">
            <span v-if="artistFilter || albumFilter">
              {{ activeFilterSummary }}
            </span>
            <span v-else>
              Sem filtro. A revisão está mostrando grupos de toda a base.
            </span>
          </p>
        </div>

        <div class="toolbar-actions">
          <button
            type="button"
            class="secondary-button"
            :disabled="!groups.length"
            @click="toggleAllVisibleGroups"
          >
            {{ allVisibleSelected ? 'Desmarcar visíveis' : 'Marcar visíveis' }}
          </button>

          <button
            type="button"
            class="secondary-button"
            :disabled="busyBatch || !selectedGroupIds.length"
            @click="openSelectedMergeConfirm"
          >
            {{ busyBatch ? 'Processando lote...' : `Mesclar ${selectedGroupIds.length} grupo(s) selecionado(s)` }}
          </button>

          <button
            type="button"
            class="primary-button"
            :disabled="busyBatch || !canMergeAllFiltered"
            @click="openFilteredMergeConfirm"
          >
            {{ busyBatch ? 'Processando lote...' : 'Mesclar todo o recorte filtrado' }}
          </button>
        </div>
      </section>

      <section class="review-summary">
        <div class="summary-metric">
          <span class="summary-label">Grupos em tela</span>
          <strong>{{ groups.length }}</strong>
        </div>
        <div class="summary-metric">
          <span class="summary-label">Com confiança alta</span>
          <strong>{{ highConfidenceCount }}</strong>
        </div>
        <div class="summary-metric">
          <span class="summary-label">Ignorados visíveis</span>
          <strong>{{ ignoredCount }}</strong>
        </div>
      </section>

      <section v-if="groups.length" class="group-list">
        <article v-for="group in groups" :key="groupId(group)" class="group-card">
          <header class="group-card__header">
            <div class="group-card__identity">
              <label class="group-select">
                <input
                  :checked="isGroupSelected(group)"
                  type="checkbox"
                  @change="toggleGroupSelection(group)"
                >
                <span>Selecionar grupo</span>
              </label>
              <p class="group-card__eyebrow">
                <NuxtLink :to="`/music/artists/${group.artistId}`">{{ group.artistName }}</NuxtLink>
                <span>·</span>
                <NuxtLink :to="`/music/albums/${group.albumId}`">{{ group.albumTitle }}</NuxtLink>
                <span v-if="group.albumYear != null">· {{ group.albumYear }}</span>
              </p>
              <h2>{{ group.candidates[0]?.title ?? group.normalizedTitle }}</h2>
              <p class="group-card__reason">{{ group.suggestionReason }}</p>
            </div>

            <div class="group-card__badges">
              <span class="confidence-badge" :data-confidence="group.confidence">
                confiança {{ confidenceLabel(group.confidence) }}
              </span>
              <span v-if="group.ignored" class="ignored-badge">ignorado</span>
            </div>
          </header>

          <div class="group-card__grid">
            <div class="group-card__cover">
              <img
                v-if="group.albumCoverUrl"
                :src="group.albumCoverUrl"
                :alt="`Capa de ${group.albumTitle}`"
              >
              <div v-else class="cover-fallback">
                {{ group.albumTitle.slice(0, 1) }}
              </div>
            </div>

            <div class="group-card__candidates">
              <div
                v-for="candidate in group.candidates"
                :key="candidate.trackId"
                class="candidate-card"
                :data-target="selectionFor(group).targetTrackId === candidate.trackId"
              >
                <div class="candidate-card__controls">
                  <label class="control-toggle">
                    <input
                      :checked="selectionFor(group).targetTrackId === candidate.trackId"
                      type="radio"
                      :name="`target-${groupId(group)}`"
                      @change="selectTarget(group, candidate.trackId)"
                    >
                    <span>Faixa canônica</span>
                  </label>

                  <label class="control-toggle">
                    <input
                      :checked="selectionFor(group).sourceTrackIds.includes(candidate.trackId)"
                      type="checkbox"
                      :disabled="selectionFor(group).targetTrackId === candidate.trackId"
                      @change="toggleSource(group, candidate.trackId)"
                    >
                    <span>Mesclar na canônica</span>
                  </label>
                </div>

                <div class="candidate-card__body">
                  <div>
                    <h3>{{ candidate.title }}</h3>
                    <p class="candidate-card__meta">
                      {{ formatPosition(candidate) }} · {{ formatDuration(candidate.durationMs) }}
                    </p>
                  </div>

                  <dl class="candidate-stats">
                    <div>
                      <dt>Playbacks</dt>
                      <dd>{{ formatCount(candidate.playbackCount) }}</dd>
                    </div>
                    <div>
                      <dt>Último play</dt>
                      <dd>{{ formatDate(candidate.lastPlayed) }}</dd>
                    </div>
                  </dl>

                  <div class="candidate-tags">
                    <span v-if="candidate.hasMusicBrainz" class="tag tag--mb">MusicBrainz</span>
                    <span v-if="candidate.hasSpotify" class="tag tag--spotify">Spotify</span>
                    <span v-if="!candidate.externalIdentifiers.length" class="tag">sem IDs externos</span>
                    <span
                      v-for="externalId in candidate.externalIdentifiers.slice(0, 3)"
                      :key="externalId"
                      class="tag"
                    >
                      {{ externalId }}
                    </span>
                  </div>
                </div>
              </div>
            </div>
          </div>

          <footer class="group-card__footer">
            <p class="merge-preview">
              <span>Canônica selecionada:</span>
              <strong>#{{ selectionFor(group).targetTrackId }}</strong>
              <span>·</span>
              <span>{{ selectionFor(group).sourceTrackIds.length }} faixa(s) serão absorvidas</span>
            </p>

            <div class="group-card__footer-actions">
              <button
                type="button"
                class="secondary-button"
                :disabled="busyGroupId === groupId(group)"
                @click="toggleIgnored(group)"
              >
                {{ group.ignored ? 'Reabrir grupo' : 'Ignorar grupo' }}
              </button>

              <button
                type="button"
                class="primary-button"
                :disabled="busyGroupId === groupId(group) || !selectionFor(group).sourceTrackIds.length"
                @click="mergeGroup(group)"
              >
                {{ busyGroupId === groupId(group) ? 'Processando...' : 'Mesclar selecionadas' }}
              </button>
            </div>
          </footer>
        </article>
      </section>

      <div v-else class="state-card">
        <p>Nenhum grupo suspeito encontrado para os filtros atuais.</p>
      </div>

      <div v-if="feedback" class="feedback-banner" :data-tone="feedback.tone">
        {{ feedback.message }}
      </div>

      <div v-if="nextCursor" class="load-more-row">
        <button type="button" class="secondary-button" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? 'Buscando mais grupos...' : 'Carregar mais grupos' }}
        </button>
      </div>

      <div v-if="confirmState" class="confirm-backdrop" @click.self="closeConfirm">
        <section class="confirm-dialog">
          <p class="review-kicker">Confirmação</p>
          <h2>{{ confirmState.title }}</h2>
          <p class="confirm-message">{{ confirmState.message }}</p>

          <div v-if="confirmState.details.length" class="confirm-details">
            <p v-for="detail in confirmState.details" :key="detail">{{ detail }}</p>
          </div>

          <div class="confirm-actions">
            <button type="button" class="secondary-button" :disabled="busyBatch" @click="closeConfirm">
              Cancelar
            </button>
            <button type="button" class="primary-button" :disabled="busyBatch" @click="runConfirmedAction">
              {{ busyBatch ? 'Processando...' : confirmState.confirmLabel }}
            </button>
          </div>
        </section>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import type {
  DuplicateTrackBatchMergeResponse,
  DuplicateTrackGroupResponse,
  DuplicateTrackMergeResponse,
  DuplicateTrackReviewPageResponse,
} from '~/types/music'

type GroupSelection = {
  targetTrackId: number
  sourceTrackIds: number[]
}

type FeedbackState = {
  tone: 'success' | 'error'
  message: string
}

type ConfirmState = {
  title: string
  message: string
  confirmLabel: string
  details: string[]
  action: 'merge-selected' | 'merge-filtered'
}

const config = useRuntimeConfig()
const includeIgnored = ref(false)
const artistFilterInput = ref('')
const albumFilterInput = ref('')
const groups = ref<DuplicateTrackGroupResponse[]>([])
const nextCursor = ref<string | null>(null)
const loadingMore = ref(false)
const busyGroupId = ref<string | null>(null)
const busyBatch = ref(false)
const feedback = ref<FeedbackState | null>(null)
const selections = ref<Record<string, GroupSelection>>({})
const selectedGroupIds = ref<string[]>([])
const confirmState = ref<ConfirmState | null>(null)

const artistFilter = ref('')
const albumFilter = ref('')
const minimumFilterLength = 2

let filterTimer: ReturnType<typeof setTimeout> | null = null

function normalizeAutoFilter(value: string) {
  const trimmed = value.trim()
  return trimmed.length >= minimumFilterLength ? trimmed : ''
}

watch([artistFilterInput, albumFilterInput], () => {
  if (filterTimer) {
    clearTimeout(filterTimer)
  }

  filterTimer = setTimeout(() => {
    artistFilter.value = normalizeAutoFilter(artistFilterInput.value)
    albumFilter.value = normalizeAutoFilter(albumFilterInput.value)
  }, 500)
})

const { data, error, status } = await useAsyncData(
  'music-duplicate-review-page',
  () => $fetch<DuplicateTrackReviewPageResponse>('/api/music/admin/track-duplicates', {
    baseURL: config.public.apiBase,
    query: {
      limit: 20,
      includeIgnored: includeIgnored.value,
      artist: artistFilter.value || undefined,
      album: albumFilter.value || undefined,
    },
  }),
  {
    watch: [includeIgnored, artistFilter, albumFilter],
  },
)

watch(data, (value) => {
  groups.value = value?.items ?? []
  nextCursor.value = value?.nextCursor ?? null
  syncSelections(groups.value)
  selectedGroupIds.value = selectedGroupIds.value.filter((id) => groups.value.some((group) => groupId(group) === id))
}, { immediate: true })

const showInitialLoading = computed(() => status.value === 'pending' && !groups.value.length)
const isRefreshingFilters = computed(() => status.value === 'pending' && groups.value.length > 0)
const ignoredArtistInput = computed(() => artistFilterInput.value.trim().length > 0 && artistFilter.value === '')
const ignoredAlbumInput = computed(() => albumFilterInput.value.trim().length > 0 && albumFilter.value === '')
const highConfidenceCount = computed(() => groups.value.filter((group) => group.confidence === 'high').length)
const ignoredCount = computed(() => groups.value.filter((group) => group.ignored).length)
const allVisibleSelected = computed(() => groups.value.length > 0 && groups.value.every((group) => selectedGroupIds.value.includes(groupId(group))))
const canMergeAllFiltered = computed(() => (artistFilter.value || albumFilter.value) && !status.value?.includes?.('pending'))
const activeFilterSummary = computed(() => {
  const parts = []
  if (artistFilter.value) parts.push(`artista: ${artistFilter.value}`)
  if (albumFilter.value) parts.push(`álbum: ${albumFilter.value}`)
  return `Mostrando apenas grupos com ${parts.join(' · ')}.`
})
const filterStatusMessage = computed(() => {
  if (isRefreshingFilters.value) return 'Atualizando resultados...'
  if (ignoredArtistInput.value && ignoredAlbumInput.value) return `Continue digitando. Os filtros passam a valer com pelo menos ${minimumFilterLength} caracteres em cada campo.`
  if (ignoredArtistInput.value) return `Continue digitando o artista. A busca automática começa com pelo menos ${minimumFilterLength} caracteres.`
  if (ignoredAlbumInput.value) return `Continue digitando o álbum. A busca automática começa com pelo menos ${minimumFilterLength} caracteres.`
  return 'Os filtros aplicam automaticamente após uma breve pausa na digitação.'
})

function groupId(group: Pick<DuplicateTrackGroupResponse, 'albumId' | 'groupKey'>) {
  return `${group.albumId}:${group.groupKey}`
}

function syncSelections(items: DuplicateTrackGroupResponse[]) {
  const nextSelections: Record<string, GroupSelection> = {}

  for (const group of items) {
    const key = groupId(group)
    const current = selections.value[key]
    const fallbackTarget = group.suggestedTargetTrackId
    const targetTrackId =
      current?.targetTrackId && group.candidates.some((candidate) => candidate.trackId === current.targetTrackId)
        ? current.targetTrackId
        : fallbackTarget

    const sourceTrackIds =
      current?.sourceTrackIds.filter((trackId) => (
        trackId !== targetTrackId && group.candidates.some((candidate) => candidate.trackId === trackId)
      )) ??
      group.candidates
        .map((candidate) => candidate.trackId)
        .filter((trackId) => trackId !== targetTrackId)

    nextSelections[key] = {
      targetTrackId,
      sourceTrackIds,
    }
  }

  selections.value = nextSelections
}

function selectionFor(group: DuplicateTrackGroupResponse): GroupSelection {
  return selections.value[groupId(group)]
}

function isGroupSelected(group: DuplicateTrackGroupResponse) {
  return selectedGroupIds.value.includes(groupId(group))
}

function toggleGroupSelection(group: DuplicateTrackGroupResponse) {
  const key = groupId(group)
  const isSelected = selectedGroupIds.value.includes(key)
  selectedGroupIds.value = isSelected
    ? selectedGroupIds.value.filter((value) => value !== key)
    : [...selectedGroupIds.value, key]
}

function toggleAllVisibleGroups() {
  if (allVisibleSelected.value) {
    const visible = new Set(groups.value.map((group) => groupId(group)))
    selectedGroupIds.value = selectedGroupIds.value.filter((id) => !visible.has(id))
    return
  }

  selectedGroupIds.value = Array.from(new Set([...selectedGroupIds.value, ...groups.value.map((group) => groupId(group))]))
}

function selectTarget(group: DuplicateTrackGroupResponse, targetTrackId: number) {
  const key = groupId(group)
  const current = selectionFor(group)
  selections.value[key] = {
    targetTrackId,
    sourceTrackIds: current.sourceTrackIds.filter((trackId) => trackId !== targetTrackId),
  }
}

function toggleSource(group: DuplicateTrackGroupResponse, trackId: number) {
  const key = groupId(group)
  const current = selectionFor(group)
  if (current.targetTrackId === trackId) return

  const alreadySelected = current.sourceTrackIds.includes(trackId)
  selections.value[key] = {
    targetTrackId: current.targetTrackId,
    sourceTrackIds: alreadySelected
      ? current.sourceTrackIds.filter((value) => value !== trackId)
      : [...current.sourceTrackIds, trackId].sort((left, right) => left - right),
  }
}

async function toggleIgnored(group: DuplicateTrackGroupResponse) {
  const key = groupId(group)
  busyGroupId.value = key
  feedback.value = null

  try {
    await $fetch('/api/music/admin/track-duplicates/ignore', {
      method: 'POST',
      baseURL: config.public.apiBase,
      body: {
        albumId: group.albumId,
        groupKey: group.groupKey,
        ignored: !group.ignored,
      },
    })

    if (!includeIgnored.value && !group.ignored) {
      groups.value = groups.value.filter((item) => groupId(item) !== key)
      syncSelections(groups.value)
    } else {
      groups.value = groups.value.map((item) => (
        groupId(item) === key
          ? { ...item, ignored: !item.ignored }
          : item
      ))
    }

    feedback.value = {
      tone: 'success',
      message: group.ignored ? 'Grupo reaberto para revisão.' : 'Grupo marcado como ignorado.',
    }
  } catch (requestError) {
    feedback.value = {
      tone: 'error',
      message: errorMessage(requestError, 'Não foi possível atualizar o estado do grupo.'),
    }
  } finally {
    busyGroupId.value = null
  }
}

async function mergeGroup(group: DuplicateTrackGroupResponse) {
  const key = groupId(group)
  const selection = selectionFor(group)
  if (!selection.sourceTrackIds.length) return

  busyGroupId.value = key
  feedback.value = null

  try {
    const response = await $fetch<DuplicateTrackMergeResponse>('/api/music/admin/track-duplicates/merge', {
      method: 'POST',
      baseURL: config.public.apiBase,
      body: {
        albumId: group.albumId,
        groupKey: group.groupKey,
        targetTrackId: selection.targetTrackId,
        sourceTrackIds: selection.sourceTrackIds,
      },
    })

    groups.value = groups.value.filter((item) => groupId(item) !== key)
    syncSelections(groups.value)
    feedback.value = {
      tone: 'success',
      message: `Merge concluído. ${response.mergedTrackIds.length} faixa(s) foram absorvidas em #${response.targetTrackId}.`,
    }
  } catch (requestError) {
    feedback.value = {
      tone: 'error',
      message: errorMessage(requestError, 'Não foi possível concluir o merge.'),
    }
  } finally {
    busyGroupId.value = null
  }
}

async function mergeSelectedGroups() {
  const selectedGroups = groups.value.filter((group) => selectedGroupIds.value.includes(groupId(group)))
  if (!selectedGroups.length) return

  busyBatch.value = true
  feedback.value = null

  try {
    const response = await $fetch<DuplicateTrackBatchMergeResponse>('/api/music/admin/track-duplicates/merge-batch', {
      method: 'POST',
      baseURL: config.public.apiBase,
      body: {
        merges: selectedGroups.map((group) => ({
          albumId: group.albumId,
          groupKey: group.groupKey,
          targetTrackId: selectionFor(group).targetTrackId,
          sourceTrackIds: selectionFor(group).sourceTrackIds,
        })).filter((merge) => merge.sourceTrackIds.length > 0),
      },
    })

    removeMergedGroups(response.results)
    feedback.value = {
      tone: 'success',
      message: `Lote concluído. ${response.processedGroups} grupo(s) foram mesclados.`,
    }
  } catch (requestError) {
    feedback.value = {
      tone: 'error',
      message: errorMessage(requestError, 'Não foi possível concluir o merge em lote.'),
    }
  } finally {
    busyBatch.value = false
  }
}

function openSelectedMergeConfirm() {
  const selectedGroups = groups.value.filter((group) => selectedGroupIds.value.includes(groupId(group)))
  if (!selectedGroups.length) return

  const mergeableGroups = selectedGroups.filter((group) => selectionFor(group).sourceTrackIds.length > 0)
  confirmState.value = {
    title: 'Mesclar grupos selecionados',
    message: `Você está prestes a mesclar ${mergeableGroups.length} grupo(s) selecionado(s).`,
    confirmLabel: 'Confirmar merge em lote',
    details: [
      `${selectedGroups.length} grupo(s) marcados na tela atual.`,
      `${mergeableGroups.length} grupo(s) têm faixas selecionadas para absorção.`,
    ],
    action: 'merge-selected',
  }
}

async function mergeAllFilteredGroups() {
  if (!canMergeAllFiltered.value) return

  busyBatch.value = true
  feedback.value = null

  try {
    const allGroups = await fetchAllFilteredGroups()
    const merges = allGroups.map((group) => {
      const loaded = groups.value.find((item) => groupId(item) === groupId(group))
      const selection = loaded ? selectionFor(loaded) : suggestedSelectionFor(group)
      return {
        albumId: group.albumId,
        groupKey: group.groupKey,
        targetTrackId: selection.targetTrackId,
        sourceTrackIds: selection.sourceTrackIds,
      }
    }).filter((merge) => merge.sourceTrackIds.length > 0)

    const response = await $fetch<DuplicateTrackBatchMergeResponse>('/api/music/admin/track-duplicates/merge-batch', {
      method: 'POST',
      baseURL: config.public.apiBase,
      body: { merges },
    })

    removeMergedGroups(response.results)
    nextCursor.value = null
    feedback.value = {
      tone: 'success',
      message: `Merge do recorte concluído. ${response.processedGroups} grupo(s) foram mesclados para o filtro atual.`,
    }
  } catch (requestError) {
    feedback.value = {
      tone: 'error',
      message: errorMessage(requestError, 'Não foi possível mesclar todo o recorte filtrado.'),
    }
  } finally {
    busyBatch.value = false
  }
}

async function openFilteredMergeConfirm() {
  if (!canMergeAllFiltered.value) return

  busyBatch.value = true
  feedback.value = null

  try {
    const allGroups = await fetchAllFilteredGroups()
    const mergeableGroups = allGroups.filter((group) => suggestedSelectionFor(group).sourceTrackIds.length > 0)
    confirmState.value = {
      title: 'Mesclar todo o recorte filtrado',
      message: `Você está prestes a mesclar ${mergeableGroups.length} grupo(s) do recorte atual.`,
      confirmLabel: 'Confirmar merge do recorte',
      details: [
        activeFilterSummary.value,
        `${allGroups.length} grupo(s) encontrados no recorte filtrado.`,
        `${mergeableGroups.length} grupo(s) serão enviados para merge com a sugestão atual.`,
      ],
      action: 'merge-filtered',
    }
  } catch (requestError) {
    feedback.value = {
      tone: 'error',
      message: errorMessage(requestError, 'Não foi possível preparar a confirmação do merge filtrado.'),
    }
  } finally {
    busyBatch.value = false
  }
}

function closeConfirm() {
  if (busyBatch.value) return
  confirmState.value = null
}

async function runConfirmedAction() {
  if (!confirmState.value) return

  const action = confirmState.value.action
  confirmState.value = null

  if (action === 'merge-selected') {
    await mergeSelectedGroups()
    return
  }

  await mergeAllFilteredGroups()
}

async function loadMore() {
  if (!nextCursor.value || loadingMore.value) return

  loadingMore.value = true
  feedback.value = null

  try {
    const page = await $fetch<DuplicateTrackReviewPageResponse>('/api/music/admin/track-duplicates', {
      baseURL: config.public.apiBase,
      query: {
        limit: 20,
        cursor: nextCursor.value,
        includeIgnored: includeIgnored.value,
        artist: artistFilter.value || undefined,
        album: albumFilter.value || undefined,
      },
    })

    groups.value = [...groups.value, ...page.items]
    nextCursor.value = page.nextCursor
    syncSelections(groups.value)
  } catch (requestError) {
    feedback.value = {
      tone: 'error',
      message: errorMessage(requestError, 'Não foi possível carregar mais grupos.'),
    }
  } finally {
    loadingMore.value = false
  }
}

async function fetchAllFilteredGroups() {
  const aggregated: DuplicateTrackGroupResponse[] = []
  let cursor: string | null = null

  do {
    const page = await $fetch<DuplicateTrackReviewPageResponse>('/api/music/admin/track-duplicates', {
      baseURL: config.public.apiBase,
      query: {
        limit: 100,
        cursor: cursor || undefined,
        includeIgnored: includeIgnored.value,
        artist: artistFilter.value || undefined,
        album: albumFilter.value || undefined,
      },
    })

    aggregated.push(...page.items)
    cursor = page.nextCursor
  } while (cursor)

  return aggregated
}

function suggestedSelectionFor(group: DuplicateTrackGroupResponse): GroupSelection {
  return {
    targetTrackId: group.suggestedTargetTrackId,
    sourceTrackIds: group.candidates.map((candidate) => candidate.trackId).filter((trackId) => trackId !== group.suggestedTargetTrackId),
  }
}

function removeMergedGroups(results: DuplicateTrackMergeResponse[]) {
  const mergedKeys = new Set(results.map((result) => `${result.albumId}:${result.groupKey}`))
  groups.value = groups.value.filter((group) => !mergedKeys.has(groupId(group)))
  selectedGroupIds.value = selectedGroupIds.value.filter((id) => !mergedKeys.has(id))
  syncSelections(groups.value)
}

function confidenceLabel(value: string) {
  switch (value) {
    case 'high':
      return 'alta'
    case 'medium':
      return 'média'
    default:
      return 'baixa'
  }
}

function formatPosition(candidate: { discNumber: number | null, trackNumber: number | null }) {
  if (candidate.discNumber != null && candidate.trackNumber != null) {
    return `Disco ${candidate.discNumber} · Faixa ${candidate.trackNumber}`
  }

  if (candidate.trackNumber != null) {
    return `Faixa ${candidate.trackNumber}`
  }

  return 'Sem posição registrada'
}

function formatDuration(durationMs: number | null) {
  if (durationMs == null || durationMs <= 0) return 'Duração ausente'

  const totalSeconds = Math.floor(durationMs / 1000)
  const minutes = Math.floor(totalSeconds / 60)
  const seconds = totalSeconds % 60
  return `${minutes}:${seconds.toString().padStart(2, '0')}`
}

function formatDate(value: string | null) {
  if (!value) return 'Sem plays'

  return new Intl.DateTimeFormat('pt-BR', {
    dateStyle: 'medium',
    timeStyle: 'short',
  }).format(new Date(value))
}

function formatCount(value: number) {
  return new Intl.NumberFormat('pt-BR').format(value)
}

function errorMessage(requestError: unknown, fallback: string) {
  const statusMessage = (requestError as { data?: { message?: string }, message?: string })?.data?.message
  return statusMessage || (requestError as { message?: string })?.message || fallback
}

useHead({
  title: 'Revisão de Duplicatas · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Ferramenta administrativa para revisar e mesclar faixas duplicadas da biblioteca musical.',
    },
  ],
})
</script>

<style scoped>
.duplicate-review-page {
  display: grid;
  gap: var(--sema-space-block);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.review-hero,
.review-filters,
.review-toolbar,
.review-summary,
.admin-subheader,
.group-card,
.state-card {
  border: 1px solid color-mix(in srgb, var(--base-color-border) 74%, white);
}

.admin-subheader {
  display: grid;
  grid-template-columns: minmax(0, 1.4fr) auto;
  gap: 20px;
  align-items: end;
  padding: 20px 24px;
  border-radius: 24px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 243, 238, 0.92));
}

.review-filters {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px 18px;
  align-items: end;
}

.filter-status {
  grid-column: 1 / -1;
  margin: 0;
  color: color-mix(in srgb, var(--base-color-text) 66%, white);
  font-size: 0.92rem;
  line-height: 1.4;
}

.filter-status[data-pending='true'] {
  color: var(--accent-color-danger-strong);
}

.admin-subheader__copy {
  display: grid;
  gap: 8px;
}

.admin-subheader__copy h2 {
  margin: 0;
  color: var(--base-color-text-primary);
  font-size: 1.75rem;
  line-height: 1;
  letter-spacing: -0.04em;
}

.admin-subheader__summary {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.admin-subheader__nav {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  justify-content: flex-end;
}

.admin-nav-link {
  display: inline-flex;
  align-items: center;
  padding: 8px 14px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 86%, white);
  color: var(--base-color-text-primary);
  font-size: 0.82rem;
}

.admin-nav-link--active {
  background: color-mix(in srgb, var(--base-color-brand-red) 14%, white);
  border: 1px solid color-mix(in srgb, var(--base-color-brand-red) 24%, white);
}

.review-hero {
  display: grid;
  grid-template-columns: minmax(0, 1.8fr) minmax(280px, 0.9fr);
  gap: 24px;
  padding: 28px;
  border-radius: 28px;
  background:
    radial-gradient(circle at top left, rgba(230, 0, 35, 0.12), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(246, 243, 238, 0.98));
}

.review-kicker,
.group-card__eyebrow,
.summary-label {
  margin: 0;
  color: var(--base-color-text-muted);
  letter-spacing: 0.08em;
  text-transform: uppercase;
  font-size: 0.76rem;
}

.review-hero h1,
.confirm-dialog h2,
.group-card h2,
.candidate-card h3 {
  margin: 0;
  color: var(--base-color-text-primary);
}

.review-hero h1 {
  font-size: clamp(2.2rem, 4.6vw, 4rem);
  line-height: 0.98;
  letter-spacing: -0.03em;
}

.review-intro,
.group-card__reason,
.merge-preview,
.toolbar-summary,
.candidate-card__meta,
.state-card,
pre {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.review-hero__copy,
.review-hero__actions,
.group-card,
.candidate-card,
.group-card__footer,
.candidate-card__body,
.group-card__candidates {
  display: grid;
  gap: 16px;
}

.review-hero__actions {
  align-content: space-between;
  justify-items: start;
}

.secondary-link,
.toggle-card,
.secondary-button,
.primary-button,
.control-toggle {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 14px;
  border-radius: 16px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 84%, white);
  background: color-mix(in srgb, var(--base-color-surface-warm) 86%, white);
  color: var(--base-color-text-primary);
  font-size: 0.82rem;
}

.secondary-link,
.secondary-button,
.primary-button {
  cursor: pointer;
}

.toggle-card input,
.control-toggle input {
  margin: 0;
}

.secondary-link:hover,
.secondary-button:hover,
.toggle-card:hover,
.control-toggle:hover {
  background: color-mix(in srgb, var(--base-color-surface-warm) 72%, white);
}

.review-summary {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 16px;
  padding: 20px 24px;
  border-radius: 24px;
  background: color-mix(in srgb, white 90%, var(--base-color-surface-soft));
}

.review-filters {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 16px;
  padding: 20px 24px;
  border-radius: 24px;
  background: color-mix(in srgb, white 88%, var(--base-color-surface-soft));
}

.review-toolbar {
  display: grid;
  grid-template-columns: minmax(0, 1.2fr) minmax(320px, 1fr);
  gap: 16px;
  align-items: start;
  padding: 20px 24px;
  border-radius: 24px;
  background: color-mix(in srgb, white 88%, var(--base-color-surface-soft));
}

.toolbar-copy,
.toolbar-actions {
  display: grid;
  gap: 10px;
}

.toolbar-actions {
  grid-template-columns: repeat(3, minmax(0, 1fr));
}

.filter-field {
  display: grid;
  gap: 8px;
  padding: 0;
  border-radius: 0;
  border: 0;
  background: transparent;
}

.filter-field input {
  width: 100%;
  padding: 11px 15px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 74%, white);
  border-radius: 16px;
  background: white;
  color: var(--base-color-text-primary);
}

.filter-field span {
  color: var(--base-color-text-muted);
  font-size: 0.75rem;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.summary-metric {
  display: grid;
  gap: 6px;
}

.summary-metric strong {
  font-size: clamp(1.4rem, 3vw, 2rem);
}

.group-list {
  display: grid;
  gap: 22px;
}

.group-card {
  padding: 22px;
  border-radius: 28px;
  background: color-mix(in srgb, white 92%, var(--base-color-surface-soft));
}

.group-card__header,
.group-card__footer,
.group-card__footer-actions,
.candidate-card__controls,
.candidate-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.group-card__grid {
  display: grid;
  grid-template-columns: 170px minmax(0, 1fr);
  gap: 20px;
}

.group-select {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  margin-bottom: 6px;
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
}

.group-card__cover img,
.cover-fallback {
  width: 100%;
  aspect-ratio: 1;
  border-radius: 24px;
  object-fit: cover;
}

.cover-fallback {
  display: grid;
  place-items: center;
  background: linear-gradient(135deg, rgba(230, 0, 35, 0.12), rgba(224, 224, 217, 0.9));
  color: var(--base-color-text-primary);
  font-size: 3rem;
  font-weight: 700;
}

.candidate-card {
  padding: 16px;
  border-radius: 20px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 74%, white);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.9));
}

.candidate-card[data-target="true"] {
  border-color: color-mix(in srgb, var(--base-color-brand-red) 34%, white);
  background: linear-gradient(180deg, rgba(255, 248, 249, 0.96), rgba(247, 241, 236, 0.92));
}

.candidate-stats {
  display: flex;
  flex-wrap: wrap;
  gap: 18px;
  margin: 0;
}

.candidate-stats div {
  display: grid;
  gap: 4px;
}

.candidate-stats dt {
  color: var(--base-color-text-muted);
  font-size: 0.76rem;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.candidate-stats dd {
  margin: 0;
  color: var(--base-color-text-primary);
  font-weight: 600;
}

.tag,
.confidence-badge,
.ignored-badge {
  display: inline-flex;
  align-items: center;
  padding: 6px 10px;
  border-radius: 12px;
  font-size: 0.75rem;
}

.tag {
  background: color-mix(in srgb, var(--base-color-surface-warm) 80%, white);
  color: var(--base-color-text-secondary);
}

.tag--mb {
  background: rgba(41, 95, 187, 0.14);
  color: #1f458c;
}

.tag--spotify {
  background: rgba(29, 185, 84, 0.14);
  color: #167243;
}

.confidence-badge[data-confidence="high"] {
  background: rgba(29, 185, 84, 0.15);
  color: #167243;
}

.confidence-badge[data-confidence="medium"] {
  background: rgba(227, 151, 14, 0.16);
  color: #8c5300;
}

.confidence-badge[data-confidence="low"] {
  background: rgba(33, 25, 34, 0.08);
  color: var(--base-color-text-secondary);
}

.ignored-badge {
  background: rgba(230, 0, 35, 0.12);
  color: #9c1832;
}

.primary-button {
  border-color: transparent;
  background: var(--base-color-brand-red);
  color: #000;
  border-radius: 16px;
}

.primary-button:hover {
  background: color-mix(in srgb, var(--base-color-brand-red) 88%, black);
}

.secondary-button {
  color: var(--base-color-text-primary);
  border-radius: 16px;
}

.primary-button:disabled,
.secondary-button:disabled {
  opacity: 0.65;
  cursor: default;
}

.state-card {
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
}

.state-card--error,
.feedback-banner[data-tone="error"] {
  color: #7a1414;
}

.feedback-banner {
  position: sticky;
  bottom: 18px;
  z-index: 5;
  justify-self: center;
  padding: 14px 18px;
  border-radius: 16px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 74%, white);
  background: rgba(255, 255, 255, 0.94);
}

.confirm-backdrop {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(33, 25, 34, 0.24);
  backdrop-filter: blur(10px);
}

.confirm-dialog {
  display: grid;
  gap: 16px;
  width: min(560px, 100%);
  padding: 24px;
  border-radius: 28px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 74%, white);
  background: color-mix(in srgb, var(--base-color-surface-strong) 92%, var(--base-color-surface-soft));
  box-shadow: var(--base-shadow-soft);
}

.confirm-dialog h2,
.confirm-message,
.confirm-details p {
  margin: 0;
}

.confirm-message,
.confirm-details p {
  color: var(--base-color-text-secondary);
}

.confirm-details {
  display: grid;
  gap: 8px;
  padding: 14px 16px;
  border-radius: 20px;
  background: color-mix(in srgb, var(--base-color-surface-soft) 84%, white);
}

.confirm-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
}

.feedback-banner[data-tone="success"] {
  color: #135c2f;
}

.load-more-row {
  display: flex;
  justify-content: center;
}

pre {
  margin-top: 12px;
  white-space: pre-wrap;
}

@media (max-width: 980px) {
  .admin-subheader,
  .review-hero,
  .group-card__grid,
  .review-summary,
  .review-filters,
  .review-toolbar,
  .toolbar-actions {
    grid-template-columns: 1fr;
  }

  .admin-subheader__nav {
    justify-content: flex-start;
  }

  .review-hero__actions {
    justify-items: start;
  }
}

@media (max-width: 720px) {
  .duplicate-review-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }

  .group-card,
  .admin-subheader,
  .review-hero,
  .review-summary,
  .review-filters,
  .review-toolbar,
  .confirm-dialog {
    padding: 18px;
  }
}
</style>
