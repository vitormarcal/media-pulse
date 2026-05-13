<template>
  <article class="terms-card" :class="{ embedded }">
    <div class="terms-head">
      <div class="terms-copy">
        <p class="eyebrow">{{ embedded ? 'Marcações do álbum' : 'Marcações' }}</p>
        <h2 v-if="!embedded">Gêneros e tags</h2>
      </div>
      <span class="summary-pill">{{ terms.visibleCount }} ativas</span>
    </div>

    <div class="term-groups">
      <section v-for="group in visibleGroups" :key="group.id" class="group-row">
        <p class="group-label">{{ group.title }}</p>

        <div class="chip-list">
          <component
            :is="editingEnabled ? 'article' : NuxtLink"
            v-for="item in group.items"
            :key="item.id"
            :to="editingEnabled ? undefined : item.href"
            class="term-pill"
            :class="[{ editing: editingEnabled }, item.kind.toLowerCase()]"
          >
            <span class="term-name">{{ item.name }}</span>
            <small v-if="editingEnabled" class="term-source">Manual</small>

            <div v-if="editingEnabled" class="pill-actions">
              <button
                type="button"
                class="pill-action"
                :disabled="busyTermId === item.termId"
                @click="updateAlbumVisibility(item.termId, true)"
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

      <p v-if="!visibleGroups.length" class="empty-copy">Ainda não há marcações ativas para este álbum.</p>
    </div>

    <div v-if="editingEnabled" class="editor-panel">
      <form class="add-form" @submit.prevent="addTerm">
        <label class="field field-name">
          <span>Nova marcação</span>
          <input v-model="draftName" type="text" placeholder="Ex.: Dream Pop, Clube da Esquina, Noite chuvosa" />

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
import type { AlbumPageData, AlbumTermDto, AlbumTermKind, AlbumTermSuggestionDto } from '~/types/music'

const props = defineProps<{
  albumId: number
  terms: AlbumPageData['terms']
  embedded?: boolean
  editing?: boolean
}>()

const emit = defineEmits<{
  changed: []
}>()

const config = useRuntimeConfig()
const draftName = ref('')
const draftKind = ref<AlbumTermKind>('TAG')
const saving = ref(false)
const searching = ref(false)
const busyTermId = ref<number | null>(null)
const feedback = ref<string | null>(null)
const suggestions = ref<AlbumTermSuggestionDto[]>([])
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

async function addTerm() {
  if (!draftName.value.trim()) return

  saving.value = true
  feedback.value = null

  try {
    await $fetch<AlbumTermDto>(`/api/music/albums/${props.albumId}/terms`, {
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
    suggestions.value = await $fetch<AlbumTermSuggestionDto[]>(`/api/music/terms/search`, {
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
    return
  }

  searchTimer = setTimeout(() => {
    void fetchSuggestions()
  }, 180)
}

function suggestionMeta(item: AlbumTermSuggestionDto) {
  const assigned = assignedTermsById.value.get(item.id)
  if (assigned?.active) return 'Já ativo neste álbum'
  if (assigned?.hiddenGlobally) return 'Existe, mas está oculto globalmente'
  if (assigned?.hiddenForAlbum) return 'Existe, mas está oculto neste álbum'
  return 'Marcação existente'
}

function suggestionDisabled(item: AlbumTermSuggestionDto) {
  const assigned = assignedTermsById.value.get(item.id)
  return Boolean(assigned?.active || item.hiddenGlobally)
}

async function applySuggestion(item: AlbumTermSuggestionDto) {
  draftName.value = item.name
  draftKind.value = item.kind
  await addTerm()
}

async function updateAlbumVisibility(termId: number, hidden: boolean) {
  busyTermId.value = termId
  feedback.value = null

  try {
    await $fetch<AlbumTermDto>(`/api/music/albums/${props.albumId}/terms/${termId}/visibility`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { hidden },
    })
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível atualizar a visibilidade neste álbum.'
  } finally {
    busyTermId.value = null
  }
}

async function updateGlobalVisibility(termId: number, hidden: boolean) {
  busyTermId.value = termId
  feedback.value = null

  try {
    await $fetch<AlbumTermDto>(`/api/music/terms/${termId}/visibility`, {
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

function restore(item: AlbumPageData['terms']['groups'][number]['items'][number]) {
  if (item.hiddenGlobally) {
    void updateGlobalVisibility(item.termId, false)
    return
  }

  void updateAlbumVisibility(item.termId, false)
}

watch([draftName, draftKind, editingEnabled], scheduleSuggestionsRefresh)

onBeforeUnmount(() => {
  if (searchTimer) clearTimeout(searchTimer)
})
</script>

<style scoped>
.terms-card {
  display: grid;
  gap: 18px;
  padding: 18px;
  border-radius: 28px;
  background: color-mix(in srgb, rgba(255, 255, 255, 0.82) 86%, var(--base-color-surface-wash));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 48%, white);
}

.terms-card.embedded {
  padding: 0;
  border: 0;
  background: transparent;
}

.terms-head,
.terms-copy,
.term-groups,
.group-row,
.chip-list,
.editor-panel,
.add-form,
.field {
  display: grid;
}

.terms-head {
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.terms-copy {
  gap: 4px;
}

.eyebrow,
.group-label,
.hidden-title {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

h2,
.empty-copy,
.feedback {
  margin: 0;
}

h2 {
  font-size: 1.4rem;
  letter-spacing: -0.04em;
}

.summary-pill,
.term-pill,
.hidden-pill,
.pill-action,
.suggestion-item,
.primary-button,
.field input,
.field select {
  border-radius: 16px;
}

.summary-pill {
  padding: 8px 12px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 88%, white);
  font-size: 0.78rem;
}

.term-groups,
.group-row,
.editor-panel {
  gap: 12px;
}

.chip-list {
  grid-template-columns: repeat(auto-fit, minmax(11rem, max-content));
  gap: 10px;
}

.term-pill,
.hidden-pill {
  display: grid;
  gap: 6px;
  justify-items: start;
  padding: 10px 12px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  text-decoration: none;
  border: 0;
  font: inherit;
}

.term-pill.genre {
  background: color-mix(in srgb, var(--base-color-surface-warm) 76%, white);
}

.term-pill.tag {
  background: color-mix(in srgb, rgba(230, 0, 35, 0.12) 62%, white);
}

.term-pill.editing {
  align-content: start;
}

.term-name {
  font-weight: 700;
}

.term-source,
.suggestion-meta,
.hidden-pill small,
.feedback,
.empty-copy {
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
}

.pill-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.pill-action,
.primary-button {
  border: 0;
  padding: 8px 12px;
  font: inherit;
  cursor: pointer;
}

.pill-action {
  background: rgba(255, 255, 255, 0.75);
}

.pill-action.ghost {
  background: transparent;
  outline: 1px solid color-mix(in srgb, var(--base-color-border) 62%, white);
}

.add-form {
  grid-template-columns: minmax(0, 1fr) 12rem auto;
  gap: 12px;
  align-items: start;
}

.field {
  gap: 8px;
}

.field span {
  font-size: 0.8rem;
  color: var(--base-color-text-secondary);
}

.field input,
.field select {
  width: 100%;
  border: 1px solid var(--base-color-border);
  background: rgba(255, 255, 255, 0.88);
  padding: 11px 15px;
  font: inherit;
}

.primary-button {
  align-self: end;
  background: var(--base-color-brand-red);
  color: #111;
  min-height: 44px;
}

.suggestions-panel {
  display: grid;
  gap: 8px;
  margin-top: 4px;
  padding: 10px;
  border-radius: 20px;
  background: rgba(255, 255, 255, 0.76);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 42%, white);
}

.suggestions-list,
.hidden-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.suggestion-item,
.hidden-pill {
  cursor: pointer;
}

.suggestion-item {
  display: grid;
  gap: 2px;
  padding: 9px 12px;
  border: 0;
  background: color-mix(in srgb, var(--base-color-surface-wash) 76%, white);
  text-align: left;
}

.hidden-panel,
.hidden-group {
  display: grid;
  gap: 10px;
}

@media (max-width: 980px) {
  .add-form {
    grid-template-columns: 1fr;
  }
}
</style>
