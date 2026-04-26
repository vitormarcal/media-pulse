<template>
  <article class="terms-card" :class="{ embedded }">
    <div class="terms-head">
      <div class="terms-copy">
        <p class="eyebrow">{{ embedded ? 'Marcações do filme' : 'Marcações' }}</p>
        <h2 v-if="!embedded">Gêneros e tags</h2>
      </div>
      <span class="summary-pill">{{ terms.visibleCount }} ativas</span>
    </div>

    <div class="term-groups">
      <section v-for="group in visibleGroups" :key="group.id" class="group-row">
        <p class="group-label">{{ group.title }}</p>

        <div class="chip-list">
          <component
            v-for="item in group.items"
            :key="item.id"
            :is="editingEnabled ? 'article' : NuxtLink"
            :to="editingEnabled ? undefined : item.href"
            class="term-pill"
            :class="[
              { editing: editingEnabled },
              item.kind.toLowerCase(),
              { tmdb: item.source === 'TMDB', manual: item.source === 'USER' },
            ]"
          >
            <span class="term-name">{{ item.name }}</span>
            <small v-if="editingEnabled" class="term-source">{{ item.source === 'TMDB' ? 'TMDb' : 'Manual' }}</small>

            <div v-if="editingEnabled" class="pill-actions">
              <button
                type="button"
                class="pill-action"
                :disabled="busyTermId === item.termId"
                @click="updateMovieVisibility(item.termId, true)"
              >
                Ocultar aqui
              </button>
              <button
                type="button"
                class="pill-action ghost"
                :disabled="busyTermId === item.termId"
                @click="updateGlobalVisibility(item.termId, true)"
              >
                Ocultar geral
              </button>
            </div>
          </component>
        </div>
      </section>

      <p v-if="!visibleGroups.length" class="empty-copy">Ainda não há marcações ativas para este filme.</p>
    </div>

    <div v-if="editingEnabled" class="editor-panel">
      <div class="editor-toolbar">
        <button type="button" class="secondary-button" :disabled="syncing" @click="syncFromTmdb">
          {{ syncing ? 'Sincronizando...' : 'Sincronizar TMDb' }}
        </button>
      </div>

      <form class="add-form" @submit.prevent="addTerm">
        <label class="field field-name">
          <span>Nova marcação</span>
          <input v-model="draftName" type="text" placeholder="Ex.: Vampiros, Folk Horror, Giallo" />

          <div v-if="shouldShowSuggestions" class="suggestions-panel">
            <p v-if="searching" class="suggestions-state">Procurando marcações existentes...</p>

            <div v-else-if="suggestions.length" class="suggestions-list">
              <button
                v-for="item in suggestions"
                :key="item.id"
                type="button"
                class="suggestion-item"
                :disabled="suggestionDisabled(item)"
                @click="applySuggestion(item)"
              >
                <span class="suggestion-name">{{ item.name }}</span>
                <small class="suggestion-meta">{{ suggestionMeta(item) }}</small>
              </button>
            </div>

            <p v-else class="suggestions-state">Nenhuma marcação existente apareceu. Você pode criar uma nova.</p>
          </div>
        </label>

        <label class="field field-kind">
          <span>Tipo</span>
          <select v-model="draftKind">
            <option value="GENRE">Gênero</option>
            <option value="TAG">Tag</option>
          </select>
        </label>

        <button type="submit" class="primary-button" :disabled="saving || !draftName.trim()">
          {{ saving ? 'Salvando...' : 'Adicionar' }}
        </button>
      </form>

      <section v-if="hiddenGroups.length" class="hidden-panel">
        <p class="hidden-title">Ocultos</p>

        <div v-for="group in hiddenGroups" :key="`${group.id}-hidden`" class="hidden-group">
          <p class="group-label">{{ group.title }}</p>

          <div class="chip-list hidden-list">
            <button
              v-for="item in group.items"
              :key="item.id"
              type="button"
              class="hidden-pill"
              :disabled="busyTermId === item.termId"
              @click="restore(item)"
            >
              <span>{{ item.name }}</span>
              <small>{{ item.stateLabel }}</small>
            </button>
          </div>
        </div>
      </section>

      <p v-if="feedback" class="feedback">{{ feedback }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import type {
  MoviePageData,
  MovieTermDto,
  MovieTermKind,
  MovieTermSuggestionDto,
  MovieTermsSyncResponse,
} from '~/types/movies'

const props = defineProps<{
  movieId: number
  terms: MoviePageData['terms']
  embedded?: boolean
  editing?: boolean
}>()

const emit = defineEmits<{
  changed: []
}>()

const config = useRuntimeConfig()
const draftName = ref('')
const draftKind = ref<MovieTermKind>('TAG')
const syncing = ref(false)
const saving = ref(false)
const searching = ref(false)
const busyTermId = ref<number | null>(null)
const feedback = ref<string | null>(null)
const suggestions = ref<MovieTermSuggestionDto[]>([])
const editingEnabled = computed(() => props.editing ?? false)
let searchTimer: ReturnType<typeof setTimeout> | null = null

const visibleGroups = computed(() =>
  props.terms.groups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => item.active),
    }))
    .filter((group) => group.items.length),
)

const hiddenGroups = computed(() =>
  props.terms.groups
    .map((group) => ({
      ...group,
      items: group.items.filter((item) => !item.active),
    }))
    .filter((group) => group.items.length),
)

const assignedTermsById = computed(() => {
  const entries = props.terms.groups.flatMap((group) => group.items.map((item) => [item.termId, item] as const))
  return new Map(entries)
})

const normalizedDraftName = computed(() => draftName.value.trim().replace(/\s+/g, ' ').toLowerCase())

const shouldShowSuggestions = computed(() => editingEnabled.value && normalizedDraftName.value.length >= 2)

async function syncFromTmdb() {
  syncing.value = true
  feedback.value = null

  try {
    const response = await $fetch<MovieTermsSyncResponse>(`/api/movies/${props.movieId}/terms/sync-tmdb`, {
      baseURL: config.public.apiBase,
      method: 'POST',
    })
    feedback.value = `${response.syncedCount} marcações vindas do TMDb foram atualizadas.`
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível sincronizar as marcações do TMDb.'
  } finally {
    syncing.value = false
  }
}

async function addTerm() {
  if (!draftName.value.trim()) return

  saving.value = true
  feedback.value = null

  try {
    await $fetch<MovieTermDto>(`/api/movies/${props.movieId}/terms`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: {
        name: draftName.value.trim(),
        kind: draftKind.value,
      },
    })
    feedback.value = `Marcação "${draftName.value.trim()}" adicionada.`
    draftName.value = ''
    draftKind.value = 'TAG'
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível adicionar esta marcação.'
  } finally {
    saving.value = false
  }
}

async function fetchSuggestions() {
  if (!shouldShowSuggestions.value) {
    suggestions.value = []
    return
  }

  searching.value = true

  try {
    suggestions.value = await $fetch<MovieTermSuggestionDto[]>(`/api/movies/terms/search`, {
      baseURL: config.public.apiBase,
      query: {
        q: draftName.value.trim(),
        kind: draftKind.value,
        limit: 6,
      },
    })
  } catch {
    suggestions.value = []
  } finally {
    searching.value = false
  }
}

function scheduleSuggestionsRefresh() {
  if (searchTimer) clearTimeout(searchTimer)

  if (!shouldShowSuggestions.value) {
    suggestions.value = []
    searching.value = false
    return
  }

  searchTimer = setTimeout(() => {
    void fetchSuggestions()
  }, 180)
}

function suggestionMeta(item: MovieTermSuggestionDto) {
  const assigned = assignedTermsById.value.get(item.id)
  if (!assigned) return item.source === 'TMDB' ? 'TMDb' : 'Manual'
  if (assigned.active) return 'Já no filme'
  return 'Reativar no filme'
}

function suggestionDisabled(item: MovieTermSuggestionDto) {
  const assigned = assignedTermsById.value.get(item.id)
  return !!assigned?.active
}

async function applySuggestion(item: MovieTermSuggestionDto) {
  if (suggestionDisabled(item) || saving.value) return
  draftKind.value = item.kind
  draftName.value = item.name
  await addTerm()
  suggestions.value = []
}

async function updateMovieVisibility(termId: number, hidden: boolean) {
  busyTermId.value = termId
  feedback.value = null

  try {
    await $fetch<MovieTermDto>(`/api/movies/${props.movieId}/terms/${termId}/visibility`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { hidden },
    })
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível atualizar a visibilidade neste filme.'
  } finally {
    busyTermId.value = null
  }
}

async function updateGlobalVisibility(termId: number, hidden: boolean) {
  busyTermId.value = termId
  feedback.value = null

  try {
    await $fetch<MovieTermDto>(`/api/movies/terms/${termId}/visibility`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { hidden },
    })
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível atualizar a visibilidade global.'
  } finally {
    busyTermId.value = null
  }
}

function restore(item: MoviePageData['terms']['groups'][number]['items'][number]) {
  if (item.hiddenGlobally) {
    updateGlobalVisibility(item.termId, false)
    return
  }

  updateMovieVisibility(item.termId, false)
}

watch([draftName, draftKind, editingEnabled], () => {
  feedback.value = null
  scheduleSuggestionsRefresh()
})

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<style scoped>
.terms-card {
  display: grid;
  gap: 18px;
  padding: 22px 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.terms-card.embedded {
  gap: 14px;
  padding: 0;
  border-radius: 0;
  background: transparent;
}

.terms-head {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  align-items: start;
}

.terms-copy {
  display: grid;
  gap: 6px;
}

.eyebrow,
.group-label,
.hidden-title,
.field span {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

h2 {
  margin: 0;
  font-size: 1.4rem;
  line-height: 1;
  letter-spacing: -0.04em;
}

.head-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: flex-end;
}

.summary-pill,
.secondary-button,
.primary-button,
.pill-action,
.hidden-pill {
  border: none;
  font: inherit;
}

.summary-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 76%, white);
  color: var(--base-color-text-primary);
  font-size: 0.78rem;
}

.secondary-button,
.primary-button {
  padding: 10px 14px;
  border-radius: 16px;
  cursor: pointer;
}

.secondary-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.primary-button {
  background: var(--base-color-brand-red);
  color: #000000;
}

.term-groups,
.editor-panel,
.hidden-panel,
.hidden-group {
  display: grid;
  gap: 14px;
}

.embedded .editor-panel {
  padding-top: 8px;
  border-top: 1px solid color-mix(in srgb, var(--base-color-border) 52%, white);
}

.group-row {
  display: grid;
  gap: 10px;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.term-pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  min-height: 42px;
  padding: 8px 14px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.82);
  color: #211922;
}

.embedded .term-pill {
  background: rgba(255, 255, 255, 0.88);
}

.term-pill.genre {
  background: color-mix(in srgb, rgba(255, 255, 255, 0.94) 84%, rgba(230, 0, 35, 0.08));
}

.term-pill.tag {
  background: rgba(229, 229, 224, 0.96);
}

.term-name {
  font-size: 0.92rem;
  font-weight: 600;
}

.term-source {
  color: var(--base-color-text-secondary);
  font-size: 0.72rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.term-pill.editing {
  padding-right: 10px;
}

.pill-actions {
  display: flex;
  gap: 6px;
  align-items: center;
}

.pill-action {
  padding: 7px 10px;
  border-radius: 14px;
  background: var(--base-color-brand-red);
  color: #ffffff;
  cursor: pointer;
  font-size: 0.74rem;
}

.pill-action.ghost {
  background: rgba(255, 255, 255, 0.8);
  color: #211922;
}

.empty-copy,
.feedback {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.editor-toolbar {
  display: flex;
  justify-content: flex-start;
}

.add-form {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(9rem, 0.55fr) auto;
  gap: 12px;
  align-items: end;
}

.field {
  display: grid;
  gap: 8px;
}

.field-name {
  position: relative;
}

.field input,
.field select {
  width: 100%;
  padding: 12px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
  color: var(--base-color-text-primary);
}

.suggestions-panel {
  display: grid;
  gap: 8px;
  margin-top: 2px;
  padding: 10px 12px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.78);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 44%, white);
}

.suggestions-list {
  display: grid;
  gap: 8px;
}

.suggestions-state {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
}

.suggestion-item {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  width: 100%;
  padding: 10px 12px;
  border: none;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  text-align: left;
  cursor: pointer;
  font: inherit;
}

.suggestion-item:disabled {
  opacity: 0.66;
  cursor: default;
}

.suggestion-name {
  font-weight: 600;
}

.suggestion-meta {
  color: var(--base-color-text-secondary);
  font-size: 0.74rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
}

.hidden-list {
  gap: 8px;
}

.hidden-pill {
  display: grid;
  gap: 4px;
  padding: 10px 12px;
  border-radius: 16px;
  background: rgba(224, 224, 217, 0.88);
  color: #211922;
  cursor: pointer;
  text-align: left;
}

.hidden-pill small {
  color: var(--base-color-text-secondary);
  font-size: 0.72rem;
}

.secondary-button:disabled,
.primary-button:disabled,
.pill-action:disabled,
.hidden-pill:disabled {
  opacity: 0.6;
  cursor: default;
}

@media (max-width: 900px) {
  .terms-head,
  .add-form {
    grid-template-columns: 1fr;
    display: grid;
  }

  .head-actions {
    justify-content: flex-start;
  }
}
</style>
