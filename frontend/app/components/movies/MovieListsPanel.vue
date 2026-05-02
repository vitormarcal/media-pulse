<template>
  <section class="lists-panel">
    <SectionHeading
      eyebrow="Listas manuais"
      :title="sectionTitle"
      :description="sectionDescription"
      :summary="lists.summary"
    />

    <div v-if="lists.items.length" class="lists-masonry">
      <article v-for="item in lists.items" :key="item.id" class="list-card" :style="cardShellStyle(item)">
        <component
          :is="editingEnabled ? 'div' : NuxtLink"
          class="card-link"
          :to="editingEnabled ? undefined : item.href"
        >
          <div class="poster-mosaic" :class="[previewClass(item.previewMovies.length), posterTone(item.listId)]">
            <template v-if="item.previewMovies.length">
              <div v-for="preview in item.previewMovies.slice(0, 3)" :key="preview.id" class="poster-tile">
                <img
                  v-if="resolveMediaUrl(preview.imageUrl)"
                  :src="resolveMediaUrl(preview.imageUrl)"
                  :alt="preview.title"
                />
                <div v-else class="poster-fallback">{{ preview.title.slice(0, 1) }}</div>
              </div>
            </template>

            <div v-else class="poster-fallback poster-fallback--large">{{ item.name.slice(0, 1) }}</div>
          </div>

          <div class="card-copy">
            <p class="card-kicker">Lista manual</p>
            <h3>{{ item.name }}</h3>
            <p class="card-description">{{ item.description || fallbackDescription(item.itemCount) }}</p>
          </div>

          <div class="card-footer">
            <span class="meta-pill">{{ item.itemCount }} filmes</span>
            <span class="meta-pill meta-pill--muted">Ordem manual</span>
            <span v-if="!editingEnabled" class="open-note">Ver recorte</span>
          </div>
        </component>

        <button
          v-if="editingEnabled"
          type="button"
          class="card-action"
          :disabled="busyListId === item.listId"
          @click="removeFromList(item.listId, item.name)"
        >
          Tirar desta lista
        </button>
      </article>
    </div>

    <article v-else-if="editingEnabled" class="empty-state">
      <p class="empty-eyebrow">Curadoria manual</p>
      <h3>Este filme ainda não abriu nenhum recorte.</h3>
      <p>
        Listas manuais funcionam melhor como coleções pequenas e navegáveis. Use o modo de ajuste para ligar este filme
        a uma lista existente ou criar uma nova.
      </p>
    </article>

    <p v-else class="quiet-empty">Nenhuma lista manual foi ligada a este filme ainda.</p>

    <div v-if="editingEnabled" class="editor-panel">
      <div class="editor-head">
        <p class="editor-eyebrow">Ajustes</p>
        <p class="editor-copy">
          Adicione o filme a recortes existentes ou crie uma nova lista manual sem sair da página.
        </p>
      </div>

      <div v-if="availableLists.length" class="editor-group">
        <p class="group-label">Adicionar a uma lista existente</p>
        <div class="editor-chip-list">
          <button
            v-for="item in availableLists"
            :key="item.listId"
            type="button"
            class="editor-chip"
            :disabled="busyListId === item.listId"
            @click="attachToExistingList(item.listId)"
          >
            <span class="chip-name">{{ item.name }}</span>
            <small class="chip-meta">{{ item.itemCount }} filmes</small>
          </button>
        </div>
      </div>

      <p v-else class="editor-note">Este filme já está em todas as listas manuais disponíveis.</p>

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
import SectionHeading from '~/components/home/SectionHeading.vue'
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
const { resolveMediaUrl } = useMediaUrl()
const editingEnabled = computed(() => props.editing ?? false)
const allLists = ref<MovieListSummaryDto[]>([])
const busyListId = ref<number | null>(null)
const creating = ref(false)
const feedback = ref<string | null>(null)
const draftName = ref('')
const draftDescription = ref('')

const sectionTitle = computed(() =>
  props.lists.visibleCount ? 'Recortes que já incluem este filme' : 'Nenhuma lista manual por aqui ainda',
)

const sectionDescription = computed(() =>
  props.lists.visibleCount
    ? 'Cada lista manual vira uma porta de entrada própria para a biblioteca, com ordem e contexto definidos por você.'
    : 'Quando uma lista manual existe, ela deve ser navegável como recorte editorial, não apenas lembrada como etiqueta.',
)

const currentListIds = computed(() => new Set(props.lists.items.map((item) => item.listId)))
const availableLists = computed(() => allLists.value.filter((item) => !currentListIds.value.has(item.listId)))

function previewClass(count: number) {
  if (count <= 0) return 'poster-mosaic--empty'
  if (count === 1) return 'poster-mosaic--single'
  if (count === 2) return 'poster-mosaic--double'
  return 'poster-mosaic--triple'
}

function posterTone(listId: number) {
  return `poster-mosaic--tone-${listId % 4}`
}

function cardShellStyle(item: MoviePageData['lists']['items'][number]) {
  const heroImageUrl = resolveMediaUrl(item.coverImageUrl ?? item.previewMovies[0]?.imageUrl ?? null)
  if (!heroImageUrl) return undefined

  return {
    backgroundImage: `linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(246, 243, 238, 0.97)), radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 30%), url("${heroImageUrl}")`,
  }
}

function fallbackDescription(itemCount: number) {
  return itemCount > 1
    ? `Um recorte manual com ${itemCount} filmes para navegar fora da biblioteca geral.`
    : 'Um recorte manual enxuto, pensado como porta de entrada para revisitas e descobertas.'
}

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
.editor-panel,
.editor-group,
.create-form,
.field {
  display: grid;
  gap: 24px;
}

.lists-masonry {
  column-count: 3;
  column-gap: 20px;
}

.list-card,
.empty-state,
.editor-panel {
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  background-image:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.06), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(246, 243, 238, 0.98));
  background-size: cover;
  background-position: center;
}

.list-card,
.empty-state {
  border-radius: 32px;
  overflow: hidden;
}

.list-card {
  break-inside: avoid;
  margin-bottom: 20px;
}

.card-link {
  display: grid;
  gap: 16px;
  height: 100%;
  color: inherit;
}

.poster-mosaic {
  display: grid;
  gap: 6px;
  min-height: 13rem;
  padding: 10px;
  background: color-mix(in srgb, var(--base-color-surface-soft) 88%, white);
}

.poster-mosaic--single,
.poster-mosaic--empty {
  grid-template-columns: 1fr;
}

.poster-mosaic--double {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.poster-mosaic--triple {
  grid-template-columns: 1.15fr 0.85fr;
  grid-template-rows: repeat(2, minmax(0, 1fr));
}

.poster-mosaic--triple .poster-tile:first-child {
  grid-row: span 2;
}

.poster-mosaic--tone-0 {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(246, 243, 238, 1));
}

.poster-mosaic--tone-1 {
  background: linear-gradient(180deg, rgba(250, 245, 238, 0.95), rgba(240, 234, 225, 0.98));
}

.poster-mosaic--tone-2 {
  background: linear-gradient(180deg, rgba(245, 244, 238, 0.96), rgba(236, 235, 227, 0.98));
}

.poster-mosaic--tone-3 {
  background: linear-gradient(180deg, rgba(247, 242, 236, 0.96), rgba(242, 236, 228, 0.98));
}

.poster-tile,
.poster-fallback {
  overflow: hidden;
  border-radius: 22px;
  border: 6px solid #fff;
  background: var(--base-color-surface-strong);
}

.poster-tile img,
.poster-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.poster-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-size: 2.6rem;
  font-weight: 700;
}

.poster-fallback--large {
  min-height: 13rem;
}

.card-copy,
.card-footer,
.editor-head {
  padding: 0 22px;
}

.card-copy {
  display: grid;
  gap: 8px;
}

.card-kicker,
.empty-eyebrow,
.editor-eyebrow,
.group-label,
.field span {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

h3,
.card-description,
.editor-copy,
.editor-note,
.feedback,
.chip-meta,
.empty-state p {
  margin: 0;
}

h3 {
  font-size: 1.3rem;
  line-height: 1.04;
  letter-spacing: -0.04em;
}

.card-description,
.editor-copy,
.editor-note,
.feedback,
.empty-state p {
  color: var(--base-color-text-secondary);
  line-height: 1.55;
}

.card-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding-bottom: 22px;
}

.meta-pill,
.editor-chip,
.card-action {
  border-radius: 16px;
  font: inherit;
}

.meta-pill {
  padding: 8px 12px;
  font-size: 0.78rem;
}

.meta-pill {
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
}

.meta-pill--muted {
  color: var(--base-color-text-secondary);
}

.open-note {
  margin-left: auto;
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
  font-weight: 700;
}

.card-action {
  margin: 0 22px 22px;
  border: 0;
  padding: 10px 14px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 88%, white);
  color: var(--base-color-text-primary);
  cursor: pointer;
}

.card-action:disabled,
.editor-chip:disabled,
.secondary-button:disabled {
  cursor: wait;
  opacity: 0.7;
}

.empty-state {
  display: grid;
  gap: 12px;
  padding: 26px;
}

.empty-state h3 {
  margin: 0;
  font-size: clamp(1.8rem, 4vw, 2.6rem);
}

.quiet-empty {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.92rem;
}

.editor-panel {
  gap: 20px;
  padding: 24px;
  border-radius: 32px;
}

.editor-head {
  display: grid;
  gap: 8px;
  padding: 0;
}

.editor-chip-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.editor-chip {
  display: grid;
  gap: 2px;
  min-width: 10rem;
  border: 0;
  padding: 12px 14px;
  background: rgba(255, 255, 255, 0.86);
  color: var(--base-color-text-primary);
  text-align: left;
  cursor: pointer;
}

.chip-name {
  font-weight: 700;
}

.chip-meta {
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
}

.editor-note {
  font-size: 0.88rem;
}

.field-row {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  gap: 14px;
}

.field {
  gap: 8px;
}

.field input {
  width: 100%;
  min-width: 0;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  padding: 11px 15px;
  background: rgba(255, 255, 255, 0.92);
  color: var(--base-color-text-primary);
  font: inherit;
}

.secondary-button {
  width: fit-content;
  border: 0;
  padding: 10px 16px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  cursor: pointer;
}

.feedback {
  font-size: 0.88rem;
}

@media (max-width: 1180px) {
  .lists-masonry {
    column-count: 2;
  }
}

@media (max-width: 780px) {
  .lists-masonry {
    column-count: 1;
  }

  .field-row {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 560px) {
  .card-footer {
    align-items: start;
  }

  .open-note {
    margin-left: 0;
  }
}
</style>
