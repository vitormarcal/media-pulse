<template>
  <section class="season-enrichment-panel">
    <div class="panel-copy">
      <p class="eyebrow">Catálogo</p>
      <h2>Enriquecer episódios</h2>
      <p>
        Preencha títulos genéricos, descrições, duração e data original usando os dados da temporada no TMDb.
      </p>
    </div>

    <div class="panel-actions">
      <label class="tmdb-field">
        <span>TMDb ID</span>
        <input
          v-model="tmdbId"
          type="text"
          inputmode="numeric"
          autocomplete="off"
          placeholder="Usar vínculo salvo"
        >
      </label>

      <button type="button" class="secondary-button" :disabled="loading" @click="loadPreview">
        {{ loading ? 'Buscando...' : 'Analisar' }}
      </button>

      <button
        type="button"
        class="primary-button"
        :disabled="applying || !preview || preview.selectedFieldsCount === 0"
        @click="applyMissing"
      >
        {{ applying ? 'Aplicando...' : 'Preencher lacunas' }}
      </button>
    </div>

    <p v-if="errorMessage" class="error-message">{{ errorMessage }}</p>

    <div v-if="preview" class="preview-shell">
      <div class="preview-summary">
        <span>{{ preview.changedEpisodesCount }} episódios com melhorias</span>
        <span>{{ preview.selectedFieldsCount }} lacunas prontas</span>
        <span v-if="preview.missingTmdbEpisodesCount > 0">{{ preview.missingTmdbEpisodesCount }} episódios só no TMDb</span>
      </div>

      <div v-if="preview.seasonFields.some((field) => field.available && field.changed)" class="field-strip">
        <label
          v-for="field in preview.seasonFields.filter((item) => item.available && item.changed)"
          :key="field.field"
          class="field-choice"
        >
          <input
            type="checkbox"
            :checked="isSeasonFieldSelected(field.field)"
            @change="toggleSeasonField(field.field)"
          >
          <span>
            <strong>{{ field.label }}</strong>
            <small>{{ field.currentValue || 'Vazio' }} -> {{ field.suggestedValue }}</small>
          </span>
        </label>
      </div>

      <div class="preview-list">
        <article
          v-for="episode in visibleEpisodes"
          :key="episode.episodeId"
          class="preview-row"
        >
          <div class="episode-index">{{ formatEpisode(episode.episodeNumber) }}</div>
          <div class="preview-content">
            <div class="preview-heading">
              <h3>{{ episode.currentTitle }}</h3>
              <p v-if="episode.suggestedTitle && episode.suggestedTitle !== episode.currentTitle">
                Sugestão: {{ episode.suggestedTitle }}
              </p>
            </div>

            <div class="field-grid">
              <label
                v-for="field in episode.fields.filter((item) => item.available && item.changed)"
                :key="field.field"
                class="field-choice"
              >
                <input
                  type="checkbox"
                  :checked="isEpisodeFieldSelected(episode.episodeId, field.field)"
                  @change="toggleEpisodeField(episode.episodeId, field.field)"
                >
                <span>
                  <strong>{{ field.label }}</strong>
                  <small>{{ field.currentValue || 'Vazio' }} -> {{ field.suggestedValue }}</small>
                </span>
              </label>
            </div>
          </div>
        </article>
      </div>

      <div class="preview-footer">
        <button
          v-if="changedEpisodes.length > visibleEpisodes.length"
          type="button"
          class="ghost-button"
          @click="showAll = true"
        >
          Ver todos
        </button>
        <button type="button" class="secondary-button" :disabled="applying || selectedFieldsCount === 0" @click="applySelected">
          Aplicar seleção
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type {
  ShowSeasonEnrichmentApplyRequest,
  ShowSeasonEnrichmentApplyResponse,
  ShowSeasonEnrichmentField,
  ShowSeasonEnrichmentPreviewResponse,
} from '~/types/shows'

const props = defineProps<{
  showId: number
  seasonNumber: number
  initialTmdbId?: string | null
}>()

const emit = defineEmits<{
  applied: [response: ShowSeasonEnrichmentApplyResponse]
}>()

const config = useRuntimeConfig()
const tmdbId = ref(props.initialTmdbId ?? '')
const preview = ref<ShowSeasonEnrichmentPreviewResponse | null>(null)
const loading = ref(false)
const applying = ref(false)
const errorMessage = ref<string | null>(null)
const showAll = ref(false)
const selectedSeasonFields = ref<Set<ShowSeasonEnrichmentField>>(new Set())
const selectedEpisodeFields = ref<Record<number, ShowSeasonEnrichmentField[]>>({})

const changedEpisodes = computed(() =>
  preview.value?.episodes.filter((episode) => episode.fields.some((field) => field.available && field.changed)) ?? [],
)

const visibleEpisodes = computed(() => (showAll.value ? changedEpisodes.value : changedEpisodes.value.slice(0, 5)))

const selectedFieldsCount = computed(() => {
  const episodeFieldsCount = Object.values(selectedEpisodeFields.value).reduce((total, fields) => total + fields.length, 0)
  return selectedSeasonFields.value.size + episodeFieldsCount
})

async function loadPreview() {
  loading.value = true
  errorMessage.value = null

  try {
    const response = await $fetch<ShowSeasonEnrichmentPreviewResponse>(
      `/api/shows/${props.showId}/seasons/${props.seasonNumber}/enrichment/preview`,
      {
        baseURL: config.public.apiBase,
        method: 'POST',
        body: {
          tmdbId: tmdbId.value.trim() || null,
        },
      },
    )

    preview.value = response
    tmdbId.value = response.resolvedTmdbId
    hydrateSelection(response)
    showAll.value = false
  } catch (error) {
    errorMessage.value = resolveErrorMessage(error)
  } finally {
    loading.value = false
  }
}

async function applyMissing() {
  await apply({
    tmdbId: tmdbId.value.trim() || null,
    mode: 'MISSING',
    seasonFields: [],
    episodeFields: [],
  })
}

async function applySelected() {
  const episodeFields = Object.entries(selectedEpisodeFields.value)
    .filter(([, fields]) => fields.length > 0)
    .map(([episodeId, fields]) => ({
      episodeId: Number(episodeId),
      fields,
    }))

  await apply({
    tmdbId: tmdbId.value.trim() || null,
    mode: 'SELECTED',
    seasonFields: Array.from(selectedSeasonFields.value),
    episodeFields,
  })
}

async function apply(body: ShowSeasonEnrichmentApplyRequest) {
  applying.value = true
  errorMessage.value = null

  try {
    const response = await $fetch<ShowSeasonEnrichmentApplyResponse>(
      `/api/shows/${props.showId}/seasons/${props.seasonNumber}/enrichment/apply`,
      {
        baseURL: config.public.apiBase,
        method: 'POST',
        body,
      },
    )

    emit('applied', response)
    await loadPreview()
  } catch (error) {
    errorMessage.value = resolveErrorMessage(error)
  } finally {
    applying.value = false
  }
}

function hydrateSelection(response: ShowSeasonEnrichmentPreviewResponse) {
  selectedSeasonFields.value = new Set(
    response.seasonFields
      .filter((field) => field.selectedByDefault)
      .map((field) => field.field),
  )
  selectedEpisodeFields.value = Object.fromEntries(
    response.episodes.map((episode) => [
      episode.episodeId,
      episode.fields
        .filter((field) => field.selectedByDefault)
        .map((field) => field.field),
    ]),
  )
}

function isSeasonFieldSelected(field: ShowSeasonEnrichmentField) {
  return selectedSeasonFields.value.has(field)
}

function toggleSeasonField(field: ShowSeasonEnrichmentField) {
  const next = new Set(selectedSeasonFields.value)
  if (next.has(field)) next.delete(field)
  else next.add(field)
  selectedSeasonFields.value = next
}

function isEpisodeFieldSelected(episodeId: number, field: ShowSeasonEnrichmentField) {
  return selectedEpisodeFields.value[episodeId]?.includes(field) ?? false
}

function toggleEpisodeField(episodeId: number, field: ShowSeasonEnrichmentField) {
  const current = selectedEpisodeFields.value[episodeId] ?? []
  const next = current.includes(field)
    ? current.filter((item) => item !== field)
    : [...current, field]

  selectedEpisodeFields.value = {
    ...selectedEpisodeFields.value,
    [episodeId]: next,
  }
}

function formatEpisode(episodeNumber: number | null) {
  return episodeNumber != null ? `E${String(episodeNumber).padStart(2, '0')}` : 'Episódio'
}

function resolveErrorMessage(error: unknown) {
  const message = typeof error === 'object' && error && 'data' in error
    ? (error as { data?: { message?: string } }).data?.message
    : null
  return message || 'Não foi possível enriquecer esta temporada.'
}
</script>

<style scoped>
.season-enrichment-panel {
  display: grid;
  gap: 18px;
  padding: 20px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 86%, var(--base-color-surface-soft));
}

.panel-copy,
.preview-shell,
.preview-list,
.preview-content {
  display: grid;
}

.panel-copy {
  gap: 8px;
}

.eyebrow,
.panel-copy h2,
.panel-copy p,
.error-message,
.preview-summary,
.preview-heading h3,
.preview-heading p {
  margin: 0;
}

.eyebrow {
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

.panel-copy h2 {
  color: var(--base-color-text-primary);
  font-size: 1.45rem;
  letter-spacing: -0.03em;
}

.panel-copy p,
.preview-heading p,
.field-choice small {
  color: var(--base-color-text-secondary);
}

.panel-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: end;
  gap: 10px;
}

.tmdb-field {
  display: grid;
  width: min(100%, 15rem);
  gap: 7px;
}

.tmdb-field span {
  color: var(--base-color-text-secondary);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.tmdb-field input {
  width: 100%;
  min-height: 38px;
  padding: 9px 12px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
  color: var(--base-color-text-primary);
}

.primary-button,
.secondary-button,
.ghost-button {
  min-height: 38px;
  border: 0;
  border-radius: 16px;
  padding: 9px 13px;
  cursor: pointer;
  font-size: 0.82rem;
  white-space: nowrap;
}

.primary-button {
  background: var(--base-color-brand-red);
  color: #000000;
}

.secondary-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.ghost-button {
  background: transparent;
  color: var(--base-color-text-primary);
}

.primary-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.error-message {
  color: #7a1414;
  font-size: 0.9rem;
}

.preview-shell {
  gap: 16px;
}

.preview-summary {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.preview-summary span {
  padding: 8px 11px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 74%, white);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.field-strip,
.field-grid {
  display: grid;
  gap: 8px;
}

.field-grid {
  grid-template-columns: repeat(auto-fit, minmax(15rem, 1fr));
}

.preview-list {
  gap: 10px;
}

.preview-row {
  display: grid;
  grid-template-columns: 4rem minmax(0, 1fr);
  gap: 14px;
  padding: 14px;
  border-radius: 20px;
  background: color-mix(in srgb, white 64%, var(--base-color-surface-soft));
}

.episode-index {
  display: grid;
  place-items: center;
  width: 3.2rem;
  height: 3.2rem;
  border-radius: 50%;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.82rem;
  font-weight: 700;
}

.preview-content {
  gap: 12px;
}

.preview-heading h3 {
  color: var(--base-color-text-primary);
  font-size: 1rem;
}

.preview-heading p {
  margin-top: 5px;
  font-size: 0.86rem;
}

.field-choice {
  display: flex;
  align-items: flex-start;
  gap: 9px;
  padding: 10px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
}

.field-choice input {
  width: 16px;
  height: 16px;
  margin-top: 2px;
  accent-color: var(--base-color-brand-red);
}

.field-choice span {
  display: grid;
  gap: 4px;
  min-width: 0;
}

.field-choice strong {
  color: var(--base-color-text-primary);
  font-size: 0.84rem;
}

.field-choice small {
  overflow-wrap: anywhere;
  font-size: 0.78rem;
  line-height: 1.35;
}

.preview-footer {
  display: flex;
  flex-wrap: wrap;
  justify-content: flex-end;
  gap: 10px;
}

@media (max-width: 720px) {
  .panel-actions,
  .preview-footer {
    align-items: stretch;
    flex-direction: column;
  }

  .tmdb-field,
  .primary-button,
  .secondary-button,
  .ghost-button {
    width: 100%;
  }

  .preview-row {
    grid-template-columns: 1fr;
  }
}
</style>
