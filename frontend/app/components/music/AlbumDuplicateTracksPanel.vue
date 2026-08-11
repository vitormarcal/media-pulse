<template>
  <section class="duplicate-panel">
    <header class="panel-heading">
      <div>
        <p class="eyebrow">Saneamento do catálogo</p>
        <h2>Faixas duplicadas</h2>
        <p>Escolha a faixa canônica e quais registros devem ser absorvidos dentro deste álbum.</p>
      </div>

      <label class="ignored-toggle">
        <input v-model="includeIgnored" type="checkbox" />
        <span>Mostrar ignorados</span>
      </label>
    </header>

    <section class="manual-merge">
      <div>
        <p class="eyebrow">Seleção manual</p>
        <h3>Mesclar duas faixas deste álbum</h3>
        <p>Use quando você reconhece uma duplicata que não apareceu nas sugestões automáticas.</p>
      </div>

      <div class="manual-track-list">
        <div v-for="track in tracks" :key="track.trackId" class="manual-track-row">
          <label class="track-selection">
            <input v-model="manualTrackIds" type="checkbox" :value="track.trackId" />
            <span>
              <strong>{{ track.title }}</strong>
              <small>{{ track.position }} · {{ track.meta }}</small>
            </span>
          </label>
          <label class="canonical-choice">
            <input
              v-model="manualTargetTrackId"
              type="radio"
              name="manual-target-track"
              :value="track.trackId"
              :disabled="!manualTrackIds.includes(track.trackId)"
            />
            Manter
          </label>
        </div>
      </div>

      <button
        class="secondary-button manual-review-button"
        type="button"
        :disabled="manualTrackIds.length < 2 || manualTargetTrackId === null"
        @click="openManualConfirmation"
      >
        Revisar mesclagem manual
      </button>
    </section>

    <div v-if="pending" class="state-card">Buscando possíveis duplicatas...</div>
    <div v-else-if="loadError" class="state-card state-card--error">
      <p>{{ loadError }}</p>
      <button class="secondary-button" type="button" @click="loadGroups">Tentar novamente</button>
    </div>

    <template v-else>
      <div v-if="groups.length" class="toolbar">
        <label class="select-all">
          <input :checked="allSelected" type="checkbox" @change="toggleAll" />
          <span>{{ allSelected ? 'Desmarcar todos' : 'Selecionar todos' }}</span>
        </label>
        <button
          class="secondary-button"
          type="button"
          :disabled="busyBatch || !selectedGroupIds.length"
          @click="openBatchConfirmation"
        >
          {{ busyBatch ? 'Mesclando...' : `Mesclar ${selectedGroupIds.length} grupo(s)` }}
        </button>
      </div>

      <div v-if="groups.length" class="group-list">
        <article v-for="group in groups" :key="groupKey(group)" class="group-card">
          <header class="group-heading">
            <div>
              <label class="group-select">
                <input
                  :checked="selectedGroupIds.includes(groupKey(group))"
                  type="checkbox"
                  @change="toggleGroup(group)"
                />
                <span>Selecionar grupo</span>
              </label>
              <h3>{{ group.candidates[0]?.title ?? group.normalizedTitle }}</h3>
              <p>{{ group.suggestionReason }}</p>
            </div>
            <div class="badges">
              <span>confiança {{ confidenceLabel(group.confidence) }}</span>
              <span v-if="group.ignored">ignorado</span>
            </div>
          </header>

          <div class="candidate-grid">
            <article
              v-for="candidate in group.candidates"
              :key="candidate.trackId"
              class="candidate-card"
              :data-target="selectionFor(group).targetTrackId === candidate.trackId"
            >
              <div class="candidate-controls">
                <label>
                  <input
                    :checked="selectionFor(group).targetTrackId === candidate.trackId"
                    type="radio"
                    :name="`album-target-${groupKey(group)}`"
                    @change="selectTarget(group, candidate.trackId)"
                  />
                  <span>Canônica</span>
                </label>
                <label>
                  <input
                    :checked="selectionFor(group).sourceTrackIds.includes(candidate.trackId)"
                    type="checkbox"
                    :disabled="selectionFor(group).targetTrackId === candidate.trackId"
                    @change="toggleSource(group, candidate.trackId)"
                  />
                  <span>Absorver</span>
                </label>
              </div>

              <h4>{{ candidate.title }}</h4>
              <p>{{ formatPosition(candidate) }} · {{ formatDuration(candidate.durationMs) }}</p>
              <dl>
                <div>
                  <dt>Reproduções</dt>
                  <dd>{{ candidate.playbackCount }}</dd>
                </div>
                <div>
                  <dt>IDs externos</dt>
                  <dd>{{ candidate.externalIdentifiers.length }}</dd>
                </div>
              </dl>
              <div class="provider-tags">
                <span v-if="candidate.hasMusicBrainz">MusicBrainz</span>
                <span v-if="candidate.hasSpotify">Spotify</span>
                <span v-if="!candidate.hasMusicBrainz && !candidate.hasSpotify">Sem provedor</span>
              </div>
            </article>
          </div>

          <footer class="group-footer">
            <p>
              <strong>#{{ selectionFor(group).targetTrackId }}</strong> será mantida e
              {{ selectionFor(group).sourceTrackIds.length }} faixa(s) serão absorvidas.
            </p>
            <div>
              <button
                class="secondary-button"
                type="button"
                :disabled="busyGroupId === groupKey(group)"
                @click="toggleIgnored(group)"
              >
                {{ group.ignored ? 'Reabrir' : 'Ignorar' }}
              </button>
              <button
                class="secondary-button"
                type="button"
                :disabled="busyGroupId === groupKey(group) || !selectionFor(group).sourceTrackIds.length"
                @click="openGroupConfirmation(group)"
              >
                Revisar mesclagem
              </button>
            </div>
          </footer>
        </article>
      </div>

      <div v-else class="state-card">Nenhuma faixa duplicada pendente neste álbum.</div>
      <p v-if="feedback" class="feedback" :data-tone="feedback.tone">{{ feedback.message }}</p>
    </template>

    <div v-if="confirmation" class="confirm-backdrop" @click.self="closeConfirmation">
      <section class="confirm-dialog" role="dialog" aria-modal="true" aria-labelledby="duplicate-confirm-title">
        <p class="eyebrow">Confirmação definitiva</p>
        <h2 id="duplicate-confirm-title">{{ confirmation.title }}</h2>
        <p>{{ confirmation.message }}</p>
        <div class="confirm-actions">
          <button class="secondary-button" type="button" :disabled="confirmationBusy" @click="closeConfirmation">
            Cancelar
          </button>
          <button class="primary-button" type="button" :disabled="confirmationBusy" @click="confirmMerge">
            {{ confirmationBusy ? 'Mesclando...' : confirmation.confirmLabel }}
          </button>
        </div>
      </section>
    </div>
  </section>
</template>

<script setup lang="ts">
import type {
  DuplicateTrackBatchMergeResponse,
  DuplicateTrackCandidateResponse,
  DuplicateTrackGroupResponse,
  DuplicateTrackMergeResponse,
  DuplicateTrackReviewPageResponse,
  AlbumTrackModel,
} from '~/types/music'

type GroupSelection = { targetTrackId: number; sourceTrackIds: number[] }
type Feedback = { tone: 'success' | 'error'; message: string }
type Confirmation = {
  title: string
  message: string
  confirmLabel: string
  groupKeys: string[]
  manual: boolean
}

const props = defineProps<{ albumId: number; albumTitle: string; tracks: AlbumTrackModel[] }>()
const emit = defineEmits<{ merged: [] }>()
const config = useRuntimeConfig()
const includeIgnored = ref(false)
const pending = ref(true)
const loadError = ref('')
const groups = ref<DuplicateTrackGroupResponse[]>([])
const selections = ref<Record<string, GroupSelection>>({})
const selectedGroupIds = ref<string[]>([])
const busyGroupId = ref<string | null>(null)
const busyBatch = ref(false)
const feedback = ref<Feedback | null>(null)
const confirmation = ref<Confirmation | null>(null)
const manualTrackIds = ref<number[]>([])
const manualTargetTrackId = ref<number | null>(null)

const allSelected = computed(
  () => groups.value.length > 0 && groups.value.every((group) => selectedGroupIds.value.includes(groupKey(group))),
)
const confirmationBusy = computed(() => busyBatch.value || busyGroupId.value !== null)

watch(includeIgnored, loadGroups)
watch(manualTrackIds, (trackIds) => {
  if (manualTargetTrackId.value === null || !trackIds.includes(manualTargetTrackId.value)) {
    manualTargetTrackId.value = trackIds[0] ?? null
  }
})

async function loadGroups() {
  pending.value = true
  loadError.value = ''
  try {
    const items: DuplicateTrackGroupResponse[] = []
    let cursor: string | null = null
    do {
      const page = await $fetch<DuplicateTrackReviewPageResponse>('/api/music/admin/track-duplicates', {
        baseURL: config.public.apiBase,
        query: {
          limit: 100,
          cursor: cursor || undefined,
          includeIgnored: includeIgnored.value,
          album: props.albumTitle,
        },
      })
      items.push(...page.items.filter((group) => group.albumId === props.albumId))
      cursor = page.nextCursor
    } while (cursor)
    groups.value = items
    syncSelections(items)
    selectedGroupIds.value = selectedGroupIds.value.filter((key) => items.some((group) => groupKey(group) === key))
  } catch (error) {
    loadError.value = errorMessage(error, 'Não foi possível carregar as duplicatas deste álbum.')
  } finally {
    pending.value = false
  }
}

function groupKey(group: Pick<DuplicateTrackGroupResponse, 'albumId' | 'groupKey'>) {
  return `${group.albumId}:${group.groupKey}`
}

function syncSelections(items: DuplicateTrackGroupResponse[]) {
  selections.value = Object.fromEntries(
    items.map((group) => {
      const targetTrackId = group.suggestedTargetTrackId
      return [
        groupKey(group),
        {
          targetTrackId,
          sourceTrackIds: group.candidates.map((candidate) => candidate.trackId).filter((id) => id !== targetTrackId),
        },
      ]
    }),
  )
}

function selectionFor(group: DuplicateTrackGroupResponse) {
  return selections.value[groupKey(group)]!
}

function selectTarget(group: DuplicateTrackGroupResponse, targetTrackId: number) {
  const key = groupKey(group)
  const previous = selectionFor(group)
  const candidateIds = group.candidates.map((candidate) => candidate.trackId)
  selections.value[key] = {
    targetTrackId,
    sourceTrackIds: Array.from(new Set([...previous.sourceTrackIds, previous.targetTrackId]))
      .filter((id) => id !== targetTrackId && candidateIds.includes(id))
      .sort((left, right) => left - right),
  }
}

function toggleSource(group: DuplicateTrackGroupResponse, trackId: number) {
  const key = groupKey(group)
  const selection = selectionFor(group)
  const selected = selection.sourceTrackIds.includes(trackId)
  selections.value[key] = {
    targetTrackId: selection.targetTrackId,
    sourceTrackIds: selected
      ? selection.sourceTrackIds.filter((id) => id !== trackId)
      : [...selection.sourceTrackIds, trackId].sort((left, right) => left - right),
  }
}

function toggleGroup(group: DuplicateTrackGroupResponse) {
  const key = groupKey(group)
  selectedGroupIds.value = selectedGroupIds.value.includes(key)
    ? selectedGroupIds.value.filter((item) => item !== key)
    : [...selectedGroupIds.value, key]
}

function toggleAll() {
  selectedGroupIds.value = allSelected.value ? [] : groups.value.map(groupKey)
}

async function toggleIgnored(group: DuplicateTrackGroupResponse) {
  const key = groupKey(group)
  busyGroupId.value = key
  feedback.value = null
  try {
    await $fetch('/api/music/admin/track-duplicates/ignore', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { albumId: props.albumId, groupKey: group.groupKey, ignored: !group.ignored },
    })
    feedback.value = { tone: 'success', message: group.ignored ? 'Grupo reaberto.' : 'Grupo ignorado.' }
    await loadGroups()
  } catch (error) {
    feedback.value = { tone: 'error', message: errorMessage(error, 'Não foi possível alterar o grupo.') }
  } finally {
    busyGroupId.value = null
  }
}

function openGroupConfirmation(group: DuplicateTrackGroupResponse) {
  const selection = selectionFor(group)
  confirmation.value = {
    title: `Mesclar “${group.candidates[0]?.title ?? group.normalizedTitle}”`,
    message: `A faixa #${selection.targetTrackId} será mantida e ${selection.sourceTrackIds.length} registro(s) serão absorvidos.`,
    confirmLabel: 'Confirmar mesclagem',
    groupKeys: [groupKey(group)],
    manual: false,
  }
}

function openBatchConfirmation() {
  const mergeable = selectedGroupIds.value.filter((key) => {
    const group = groups.value.find((item) => groupKey(item) === key)
    return group && selectionFor(group).sourceTrackIds.length > 0
  })
  confirmation.value = {
    title: 'Mesclar grupos selecionados',
    message: `${mergeable.length} grupo(s) serão consolidados usando as escolhas atuais.`,
    confirmLabel: 'Confirmar lote',
    groupKeys: mergeable,
    manual: false,
  }
}

function openManualConfirmation() {
  if (manualTargetTrackId.value === null || manualTrackIds.value.length < 2) return
  const target = props.tracks.find((track) => track.trackId === manualTargetTrackId.value)
  confirmation.value = {
    title: 'Mesclar faixas selecionadas manualmente',
    message: `“${target?.title ?? `#${manualTargetTrackId.value}`}” será mantida e ${manualTrackIds.value.length - 1} faixa(s) serão absorvidas.`,
    confirmLabel: 'Confirmar mesclagem',
    groupKeys: [],
    manual: true,
  }
}

function closeConfirmation() {
  if (!confirmationBusy.value) confirmation.value = null
}

async function confirmMerge() {
  if (confirmation.value?.manual) {
    confirmation.value = null
    await mergeManualSelection()
    return
  }

  const groupKeys = confirmation.value?.groupKeys ?? []
  const selectedGroups = groups.value.filter((group) => groupKeys.includes(groupKey(group)))
  if (!selectedGroups.length) return
  confirmation.value = null
  feedback.value = null

  if (selectedGroups.length === 1) {
    await mergeOne(selectedGroups[0]!)
    return
  }

  busyBatch.value = true
  try {
    const response = await $fetch<DuplicateTrackBatchMergeResponse>('/api/music/admin/track-duplicates/merge-batch', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { merges: selectedGroups.map(mergeRequest) },
    })
    feedback.value = { tone: 'success', message: `${response.processedGroups} grupo(s) mesclados com sucesso.` }
    selectedGroupIds.value = []
    await loadGroups()
    emit('merged')
  } catch (error) {
    feedback.value = { tone: 'error', message: errorMessage(error, 'Não foi possível concluir o lote.') }
  } finally {
    busyBatch.value = false
  }
}

async function mergeManualSelection() {
  if (manualTargetTrackId.value === null) return
  busyBatch.value = true
  feedback.value = null
  try {
    const response = await $fetch<DuplicateTrackMergeResponse>('/api/music/admin/track-duplicates/manual-merge', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: {
        albumId: props.albumId,
        targetTrackId: manualTargetTrackId.value,
        sourceTrackIds: manualTrackIds.value.filter((trackId) => trackId !== manualTargetTrackId.value),
      },
    })
    feedback.value = { tone: 'success', message: `${response.mergedTrackIds.length} faixa(s) foram absorvidas.` }
    manualTrackIds.value = []
    manualTargetTrackId.value = null
    await loadGroups()
    emit('merged')
  } catch (error) {
    feedback.value = { tone: 'error', message: errorMessage(error, 'Não foi possível concluir a mesclagem manual.') }
  } finally {
    busyBatch.value = false
  }
}

async function mergeOne(group: DuplicateTrackGroupResponse) {
  const key = groupKey(group)
  busyGroupId.value = key
  try {
    const response = await $fetch<DuplicateTrackMergeResponse>('/api/music/admin/track-duplicates/merge', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: mergeRequest(group),
    })
    feedback.value = { tone: 'success', message: `${response.mergedTrackIds.length} faixa(s) foram absorvidas.` }
    await loadGroups()
    emit('merged')
  } catch (error) {
    feedback.value = { tone: 'error', message: errorMessage(error, 'Não foi possível concluir a mesclagem.') }
  } finally {
    busyGroupId.value = null
  }
}

function mergeRequest(group: DuplicateTrackGroupResponse) {
  const selection = selectionFor(group)
  return {
    albumId: props.albumId,
    groupKey: group.groupKey,
    targetTrackId: selection.targetTrackId,
    sourceTrackIds: selection.sourceTrackIds,
  }
}

function confidenceLabel(confidence: string) {
  return confidence === 'high' ? 'alta' : confidence === 'medium' ? 'média' : 'baixa'
}

function formatPosition(candidate: DuplicateTrackCandidateResponse) {
  if (candidate.discNumber != null && candidate.trackNumber != null)
    return `Disco ${candidate.discNumber} · Faixa ${candidate.trackNumber}`
  if (candidate.trackNumber != null) return `Faixa ${candidate.trackNumber}`
  return 'Sem posição'
}

function formatDuration(durationMs: number | null) {
  if (!durationMs) return 'Duração ausente'
  const totalSeconds = Math.floor(durationMs / 1000)
  return `${Math.floor(totalSeconds / 60)}:${String(totalSeconds % 60).padStart(2, '0')}`
}

function errorMessage(error: unknown, fallback: string) {
  return (
    (error as { data?: { message?: string }; message?: string })?.data?.message ||
    (error as { message?: string })?.message ||
    fallback
  )
}

await loadGroups()
</script>

<style scoped>
.duplicate-panel,
.group-list,
.group-card,
.candidate-card,
.manual-merge {
  display: grid;
  gap: 20px;
}
.manual-merge {
  padding: 20px;
  border-radius: 24px;
  background: var(--base-color-surface-soft);
}
.manual-merge h3 {
  margin: 5px 0 8px;
}
.manual-merge > div:first-child p:last-child {
  color: var(--base-color-text-secondary);
}
.manual-track-list {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(280px, 1fr));
  gap: 8px;
}
.manual-track-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 10px;
  align-items: start;
  padding: 12px;
  border-radius: 16px;
  background: white;
}
.track-selection {
  display: grid;
  grid-template-columns: auto minmax(0, 1fr);
  gap: 10px;
  align-items: start;
  min-width: 0;
}
.track-selection > span {
  display: grid;
  min-width: 0;
}
.manual-track-row strong {
  overflow-wrap: anywhere;
  line-height: 1.25;
}
.manual-track-row small {
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.manual-track-row small {
  color: var(--base-color-text-secondary);
}
.canonical-choice {
  display: flex;
  gap: 6px;
  align-items: center;
  font-size: 0.75rem;
  font-weight: 700;
  white-space: nowrap;
}
.manual-review-button {
  justify-self: start;
}
.duplicate-panel {
  padding: 24px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 65%, white);
  border-radius: 32px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(246, 243, 238, 0.98));
}
.panel-heading,
.toolbar,
.group-heading,
.group-footer,
.candidate-controls,
.confirm-actions {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: center;
}
.eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}
h2,
h3,
h4,
p,
dl {
  margin: 0;
}
.panel-heading h2 {
  margin: 5px 0 8px;
  font-size: 1.75rem;
  letter-spacing: -1.2px;
}
.panel-heading p,
.group-heading p,
.candidate-card p,
.group-footer p {
  color: var(--base-color-text-secondary);
}
.ignored-toggle,
.select-all,
.group-select,
.candidate-controls label {
  display: flex;
  gap: 8px;
  align-items: center;
  font-size: 0.8rem;
  font-weight: 700;
}
.toolbar {
  padding: 14px 16px;
  border-radius: 20px;
  background: var(--base-color-surface-warm);
}
.group-card {
  padding: 20px;
  border-radius: 24px;
  background: white;
}
.group-heading {
  align-items: flex-start;
}
.group-heading h3 {
  margin: 8px 0 5px;
}
.badges,
.provider-tags {
  display: flex;
  flex-wrap: wrap;
  gap: 6px;
}
.badges span,
.provider-tags span {
  padding: 6px 9px;
  border-radius: 12px;
  background: var(--base-color-surface-warm);
  font-size: 0.72rem;
}
.candidate-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(240px, 1fr));
  gap: 12px;
}
.candidate-card {
  padding: 16px;
  border: 2px solid transparent;
  border-radius: 20px;
  background: var(--base-color-surface-soft);
}
.candidate-card[data-target='true'] {
  border-color: var(--base-color-brand-red);
}
.candidate-card dl {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 8px;
}
.candidate-card dl div {
  padding: 10px;
  border-radius: 12px;
  background: white;
}
.candidate-card dt {
  color: var(--base-color-text-secondary);
  font-size: 0.7rem;
}
.candidate-card dd {
  margin: 3px 0 0;
  font-weight: 700;
}
.group-footer {
  padding-top: 16px;
  border-top: 1px solid var(--base-color-border);
}
.group-footer > div {
  display: flex;
  gap: 8px;
}
.primary-button,
.secondary-button {
  border: 0;
  border-radius: 16px;
  padding: 10px 16px;
  font: inherit;
  font-size: 0.8rem;
  font-weight: 700;
  cursor: pointer;
}
.primary-button {
  background: var(--base-color-brand-red);
  color: white;
}
.secondary-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}
button:disabled {
  cursor: not-allowed;
  opacity: 0.55;
}
.state-card,
.feedback {
  padding: 18px;
  border-radius: 20px;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-secondary);
}
.state-card--error,
.feedback[data-tone='error'] {
  color: #9e0a0a;
}
.feedback[data-tone='success'] {
  color: #103c25;
  background: #e7f1ea;
}
.confirm-backdrop {
  position: fixed;
  inset: 0;
  z-index: 50;
  display: grid;
  place-items: center;
  padding: 20px;
  background: rgba(33, 25, 34, 0.48);
}
.confirm-dialog {
  width: min(520px, 100%);
  padding: 28px;
  border-radius: 28px;
  background: white;
}
.confirm-dialog h2 {
  margin: 6px 0 12px;
}
.confirm-actions {
  justify-content: flex-end;
  margin-top: 24px;
}
@media (max-width: 720px) {
  .panel-heading,
  .toolbar,
  .group-heading,
  .group-footer {
    align-items: stretch;
    flex-direction: column;
  }
  .group-footer > div,
  .confirm-actions {
    flex-direction: column;
  }
  .primary-button,
  .secondary-button {
    width: 100%;
  }
  .duplicate-panel {
    padding: 18px;
  }
}
</style>
