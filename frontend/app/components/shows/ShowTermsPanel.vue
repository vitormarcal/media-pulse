<template>
  <article class="terms-card">
    <div class="terms-head">
      <p class="eyebrow">Marcações da série</p>
      <span class="summary-pill">{{ terms.visibleCount }} ativas</span>
    </div>

    <section v-for="group in visibleGroups" :key="group.id" class="term-group">
      <p class="group-label">{{ group.title }}</p>
      <div class="chip-list">
        <NuxtLink
          v-for="item in group.items"
          v-show="!editing"
          :key="`${item.id}-link`"
          class="term-pill term-link"
          :class="item.kind.toLowerCase()"
          :to="item.href"
        >
          <span>{{ item.name }}</span>
        </NuxtLink>
        <article
          v-for="item in group.items"
          v-show="editing"
          :key="`${item.id}-editor`"
          class="term-pill"
          :class="item.kind.toLowerCase()"
        >
          <span>{{ item.name }}</span>
          <small v-if="editing">{{ item.source === 'TMDB' ? 'TMDb' : 'Manual' }}</small>
          <div v-if="editing" class="pill-actions">
            <button type="button" :disabled="busyTermId === item.termId" @click="setLocalVisibility(item.termId, true)">
              Ocultar aqui
            </button>
            <button
              type="button"
              class="ghost"
              :disabled="busyTermId === item.termId"
              @click="setGlobalVisibility(item.termId, true)"
            >
              Ocultar geral
            </button>
          </div>
        </article>
      </div>
    </section>

    <p v-if="!visibleGroups.length" class="empty-copy">{{ emptyLabel }}</p>

    <div v-if="editing" class="editor-panel">
      <button
        v-if="enrichmentStatus.status !== 'BLOCKED'"
        type="button"
        class="secondary-button"
        :disabled="syncing"
        @click="syncFromTmdb"
      >
        {{ syncing ? 'Sincronizando...' : 'Sincronizar TMDb' }}
      </button>
      <p v-else class="blocked-copy">Vincule esta série ao TMDb para buscar marcações externas.</p>

      <form class="add-form" @submit.prevent="addTerm">
        <label class="field name-field">
          <span>Nova marcação</span>
          <input v-model="draftName" type="text" placeholder="Ex.: Mistério, viagem no tempo" />
          <div v-if="shouldShowSuggestions" class="suggestions-panel">
            <p v-if="searching" class="suggestions-state">Procurando marcações existentes...</p>
            <div v-else-if="suggestions.length" class="suggestions-list">
              <button
                v-for="suggestion in suggestions"
                :key="suggestion.id"
                type="button"
                class="suggestion-item"
                :disabled="suggestionDisabled(suggestion)"
                @click="applySuggestion(suggestion)"
              >
                <span>{{ suggestion.name }}</span>
                <small>{{ suggestionMeta(suggestion) }}</small>
              </button>
            </div>
            <p v-else class="suggestions-state">Nenhuma marcação existente apareceu. Você pode criar uma nova.</p>
          </div>
        </label>
        <label class="field">
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
          <div class="chip-list">
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
      <p v-if="feedback" class="feedback" role="status">{{ feedback }}</p>
    </div>
  </article>
</template>

<script setup lang="ts">
import type {
  ShowPageData,
  ShowTermDto,
  ShowTermKind,
  ShowTermSuggestionDto,
  ShowTermsSyncResponse,
} from '~/types/shows'

const props = defineProps<{
  showId: number
  terms: ShowPageData['terms']
  editing: boolean
  enrichmentStatus: ShowPageData['enrichment']['terms']
}>()
const emit = defineEmits<{ changed: [] }>()
const config = useRuntimeConfig()
const draftName = ref('')
const draftKind = ref<ShowTermKind>('TAG')
const syncing = ref(false)
const saving = ref(false)
const searching = ref(false)
const busyTermId = ref<number | null>(null)
const feedback = ref<string | null>(null)
const suggestions = ref<ShowTermSuggestionDto[]>([])
let searchTimer: ReturnType<typeof setTimeout> | null = null

const visibleGroups = computed(() =>
  props.terms.groups
    .map((group) => ({ ...group, items: group.items.filter((item) => item.active) }))
    .filter((group) => group.items.length),
)
const hiddenGroups = computed(() =>
  props.terms.groups
    .map((group) => ({ ...group, items: group.items.filter((item) => !item.active) }))
    .filter((group) => group.items.length),
)
const assignedTermsById = computed(() => {
  const entries = props.terms.groups.flatMap((group) => group.items.map((item) => [item.termId, item] as const))
  return new Map(entries)
})
const normalizedDraftName = computed(() => draftName.value.trim().replace(/\s+/g, ' ').toLowerCase())
const shouldShowSuggestions = computed(() => props.editing && normalizedDraftName.value.length >= 2)
const emptyLabel = computed(() =>
  props.enrichmentStatus.status === 'PENDING'
    ? 'Completando marcações…'
    : props.enrichmentStatus.status === 'RETRY_SCHEDULED'
      ? 'Não foi possível atualizar agora. Tentaremos novamente mais tarde.'
      : 'Ainda não há marcações ativas para esta série.',
)

async function syncFromTmdb() {
  syncing.value = true
  feedback.value = null
  try {
    const response = await $fetch<ShowTermsSyncResponse>(`/api/admin/shows/${props.showId}/terms/sync-tmdb`, {
      baseURL: config.public.apiBase,
      method: 'POST',
    })
    feedback.value = `${response.syncedCount} marcações do TMDb atualizadas.`
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
    await $fetch<ShowTermDto>(`/api/shows/${props.showId}/terms`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { name: draftName.value.trim(), kind: draftKind.value },
    })
    feedback.value = `Marcação "${draftName.value.trim()}" adicionada.`
    draftName.value = ''
    draftKind.value = 'TAG'
    suggestions.value = []
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
    searching.value = false
    return
  }
  searching.value = true
  try {
    suggestions.value = await $fetch<ShowTermSuggestionDto[]>('/api/shows/terms/search', {
      baseURL: config.public.apiBase,
      query: { q: draftName.value.trim(), kind: draftKind.value, limit: 6 },
    })
  } catch {
    suggestions.value = []
  } finally {
    searching.value = false
  }
}

function suggestionMeta(item: ShowTermSuggestionDto) {
  const assigned = assignedTermsById.value.get(item.id)
  if (!assigned) return item.source === 'TMDB' ? 'TMDb' : 'Manual'
  if (assigned.active) return 'Já nesta série'
  return 'Reativar nesta série'
}

function suggestionDisabled(item: ShowTermSuggestionDto) {
  return Boolean(assignedTermsById.value.get(item.id)?.active)
}

async function applySuggestion(item: ShowTermSuggestionDto) {
  if (suggestionDisabled(item) || saving.value) return
  draftName.value = item.name
  draftKind.value = item.kind
  await addTerm()
  suggestions.value = []
}

async function setLocalVisibility(termId: number, hidden: boolean) {
  busyTermId.value = termId
  try {
    await $fetch(`/api/shows/${props.showId}/terms/${termId}/visibility`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { hidden },
    })
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível alterar esta marcação.'
  } finally {
    busyTermId.value = null
  }
}

async function setGlobalVisibility(termId: number, hidden: boolean) {
  busyTermId.value = termId
  try {
    await $fetch(`/api/shows/terms/${termId}/visibility`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { hidden },
    })
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível alterar esta marcação.'
  } finally {
    busyTermId.value = null
  }
}

function restore(item: ShowPageData['terms']['groups'][number]['items'][number]) {
  void (item.hiddenGlobally ? setGlobalVisibility(item.termId, false) : setLocalVisibility(item.termId, false))
}

watch([draftName, draftKind, () => props.editing], () => {
  if (searchTimer) clearTimeout(searchTimer)
  if (!shouldShowSuggestions.value) {
    suggestions.value = []
    searching.value = false
    return
  }
  searchTimer = setTimeout(() => void fetchSuggestions(), 180)
})
onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<style scoped>
.terms-card,
.editor-panel,
.term-group,
.hidden-panel,
.hidden-group,
.field {
  display: grid;
  gap: 10px;
}
.terms-head,
.chip-list,
.pill-actions {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.terms-head {
  justify-content: space-between;
}
.eyebrow,
.group-label,
.hidden-title,
.field > span {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}
.summary-pill,
.term-pill,
.hidden-pill {
  border: 0;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.86);
  padding: 8px 12px;
}
.term-pill {
  display: flex;
  align-items: center;
  gap: 8px;
}
.term-pill.tag {
  background: rgba(229, 229, 224, 0.96);
}
.term-link:hover {
  color: var(--base-color-brand-red);
}
.term-link:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 2px;
}
.term-pill small,
.feedback,
.empty-copy,
.blocked-copy,
.suggestions-state {
  color: var(--base-color-text-secondary);
}
.pill-actions button,
.secondary-button,
.primary-button,
.suggestion-item {
  border: 0;
  border-radius: 16px;
  padding: 8px 11px;
  font: inherit;
  cursor: pointer;
}
.pill-actions button,
.primary-button {
  background: var(--base-color-brand-red);
}
.pill-actions .ghost,
.secondary-button,
.hidden-pill {
  background: var(--base-color-surface-warm);
}
.editor-panel {
  padding-top: 12px;
  border-top: 1px solid var(--base-color-border);
}
.secondary-button {
  width: fit-content;
}
.add-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 9rem auto;
  gap: 10px;
  align-items: end;
}
.field {
  position: relative;
}
.field input,
.field select {
  padding: 11px 13px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: #fff;
  font: inherit;
}
.suggestions-panel {
  position: absolute;
  z-index: 3;
  top: 100%;
  left: 0;
  right: 0;
  display: grid;
  padding: 8px;
  border-radius: 16px;
  background: #fff;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 60%, white);
}
.suggestions-list {
  display: grid;
}
.suggestion-item {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  background: transparent;
  text-align: left;
}
.suggestion-item small,
.hidden-pill small {
  color: var(--base-color-text-secondary);
}
.hidden-pill {
  display: inline-flex;
  gap: 8px;
  align-items: center;
}
.primary-button:focus-visible,
.secondary-button:focus-visible,
.pill-actions button:focus-visible,
.hidden-pill:focus-visible,
.suggestion-item:focus-visible,
.field input:focus-visible,
.field select:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 2px;
}
.primary-button:disabled,
.secondary-button:disabled,
.pill-actions button:disabled,
.hidden-pill:disabled,
.suggestion-item:disabled {
  cursor: wait;
  opacity: 0.62;
}
.feedback,
.empty-copy,
.blocked-copy,
.suggestions-state,
.hidden-title {
  margin: 0;
}
@media (max-width: 720px) {
  .add-form {
    grid-template-columns: 1fr;
  }
  .primary-button {
    min-height: 42px;
  }
}
</style>
