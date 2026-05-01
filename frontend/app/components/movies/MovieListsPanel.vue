<template>
  <section class="lists-panel">
    <div class="lists-head">
      <p class="eyebrow">Listas do filme</p>
      <span class="summary-pill">{{ lists.visibleCount }} ativas</span>
    </div>

    <div v-if="lists.items.length" class="chip-list">
      <component
        v-for="item in lists.items"
        :key="item.id"
        :is="editingEnabled ? 'article' : NuxtLink"
        :to="editingEnabled ? undefined : item.href"
        class="list-pill"
        :class="{ editing: editingEnabled }"
      >
        <div class="pill-copy">
          <span class="list-name">{{ item.name }}</span>
          <small class="list-meta">{{ item.itemCount }} filmes</small>
        </div>
        <button
          v-if="editingEnabled"
          type="button"
          class="pill-action"
          :disabled="busyListId === item.listId"
          @click="removeFromList(item.listId, item.name)"
        >
          Tirar
        </button>
      </component>
    </div>

    <p v-else class="empty-copy">Este filme ainda não entrou em nenhuma lista manual.</p>

    <div v-if="editingEnabled" class="editor-panel">
      <div v-if="availableLists.length" class="editor-group">
        <p class="group-label">Adicionar a uma lista existente</p>
        <div class="chip-list">
          <button
            v-for="item in availableLists"
            :key="item.listId"
            type="button"
            class="existing-pill"
            :disabled="busyListId === item.listId"
            @click="attachToExistingList(item.listId)"
          >
            <span class="list-name">{{ item.name }}</span>
            <small class="list-meta">{{ item.itemCount }} filmes</small>
          </button>
        </div>
      </div>

      <form class="create-form" @submit.prevent="createAndAttachList">
        <p class="group-label">Criar nova lista</p>

        <div class="field-row">
          <label class="field field-name">
            <span>Nome</span>
            <input v-model="draftName" type="text" placeholder="Favoritos, Oscar 2025, Vampiros essenciais" />
          </label>

          <label class="field field-description">
            <span>Descrição</span>
            <input v-model="draftDescription" type="text" placeholder="Opcional" />
          </label>
        </div>

        <button type="submit" class="secondary-button" :disabled="creating || !draftName.trim()">
          {{ creating ? 'Criando...' : 'Criar e adicionar' }}
        </button>
      </form>
    </div>

    <p v-if="feedback" class="feedback">{{ feedback }}</p>
  </section>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import type { MovieListAttachRequest, MovieListSummaryDto, MoviePageData } from '~/types/movies'

const props = defineProps<{
  movieId: number
  lists: MoviePageData['lists']
  editing?: boolean
}>()

const emit = defineEmits<{
  changed: []
}>()

const config = useRuntimeConfig()
const editingEnabled = computed(() => props.editing ?? false)
const allLists = ref<MovieListSummaryDto[]>([])
const busyListId = ref<number | null>(null)
const creating = ref(false)
const feedback = ref<string | null>(null)
const draftName = ref('')
const draftDescription = ref('')

const currentListIds = computed(() => new Set(props.lists.items.map((item) => item.listId)))
const availableLists = computed(() => allLists.value.filter((item) => !currentListIds.value.has(item.listId)))

async function loadAllLists() {
  try {
    allLists.value = await $fetch<MovieListSummaryDto[]>('/api/movies/lists', {
      baseURL: config.public.apiBase,
    })
  } catch {
    allLists.value = []
  }
}

async function ensureAllListsLoaded(force = false) {
  if (!editingEnabled.value) return
  if (!force && allLists.value.length) return
  await loadAllLists()
}

async function attachToExistingList(listId: number) {
  if (busyListId.value) return
  busyListId.value = listId
  feedback.value = null

  try {
    await $fetch<MovieListSummaryDto>(`/api/movies/${props.movieId}/lists`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: {
        listId,
        name: null,
        description: null,
      } satisfies MovieListAttachRequest,
    })
    feedback.value = 'Filme adicionado à lista.'
    await ensureAllListsLoaded(true)
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível adicionar este filme à lista.'
  } finally {
    busyListId.value = null
  }
}

async function createAndAttachList() {
  if (!draftName.value.trim()) return

  creating.value = true
  feedback.value = null
  const createdName = draftName.value.trim()

  try {
    await $fetch<MovieListSummaryDto>(`/api/movies/${props.movieId}/lists`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: {
        listId: null,
        name: createdName,
        description: draftDescription.value.trim() || null,
      } satisfies MovieListAttachRequest,
    })
    feedback.value = `Filme ligado a "${createdName}".`
    draftName.value = ''
    draftDescription.value = ''
    await ensureAllListsLoaded(true)
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível criar esta lista.'
  } finally {
    creating.value = false
  }
}

async function removeFromList(listId: number, listName: string) {
  if (busyListId.value) return
  busyListId.value = listId
  feedback.value = null

  try {
    await $fetch(`/api/movies/${props.movieId}/lists/${listId}`, {
      baseURL: config.public.apiBase,
      method: 'DELETE',
    })
    feedback.value = `Filme removido de "${listName}".`
    await ensureAllListsLoaded(true)
    emit('changed')
  } catch {
    feedback.value = 'Não foi possível remover este filme da lista.'
  } finally {
    busyListId.value = null
  }
}

watch(
  editingEnabled,
  (enabled) => {
    if (enabled) {
      void ensureAllListsLoaded()
      return
    }
    feedback.value = null
  },
  { immediate: true },
)
</script>

<style scoped>
.lists-panel,
.lists-head,
.editor-panel,
.editor-group,
.create-form,
.field {
  display: grid;
  gap: 8px;
}

.group-label,
.field span,
.empty-copy,
.feedback,
.list-meta {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.74rem;
}

.lists-head {
  grid-template-columns: auto auto;
  justify-content: space-between;
  align-items: center;
}

.eyebrow,
.group-label,
.field span {
  margin: 0;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
  color: var(--base-color-text-secondary);
  font-size: 0.72rem;
}

.summary-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.76rem;
}

.chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.list-pill,
.existing-pill {
  display: inline-flex;
  align-items: center;
  gap: 10px;
  padding: 8px 12px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.8);
  color: var(--base-color-text-primary);
}

.list-pill.editing {
  padding-right: 10px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 42%, white);
}

.existing-pill {
  border: none;
  cursor: pointer;
  font: inherit;
  text-align: left;
}

.pill-copy {
  display: grid;
  gap: 2px;
}

.list-name {
  font-size: 0.84rem;
  font-weight: 600;
  line-height: 1.1;
}

.editor-panel {
  gap: 14px;
  padding: 14px;
  border-radius: 24px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 82%, white);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 38%, white);
}

.pill-action {
  border: none;
  background: transparent;
  color: var(--base-color-brand-red);
  cursor: pointer;
  font: inherit;
  font-size: 0.72rem;
}

.field-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(16rem, 1fr);
  gap: 10px;
}

.field input {
  width: 100%;
  padding: 11px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
  color: var(--base-color-text-primary);
}

.secondary-button {
  width: fit-content;
  border: none;
  padding: 8px 14px;
  border-radius: 16px;
  cursor: pointer;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
}

.secondary-button:disabled,
.existing-pill:disabled,
.pill-action:disabled {
  opacity: 0.6;
  cursor: default;
}

@media (max-width: 720px) {
  .field-row {
    grid-template-columns: 1fr;
  }
}
</style>
