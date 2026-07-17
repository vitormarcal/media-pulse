<template>
  <main class="list-page">
    <p v-if="status === 'pending'" class="state-card">Abrindo a lista...</p>
    <p v-else-if="error" class="state-card error">Não foi possível abrir esta lista.</p>

    <template v-else-if="data">
      <section class="hero">
        <div class="hero-copy">
          <div class="hero-actions">
            <NuxtLink class="quiet-button" to="/music/lists">Voltar para listas</NuxtLink>
            <button class="quiet-button" type="button" @click="editing = !editing">
              {{ editing ? 'Fechar edição' : 'Editar lista' }}
            </button>
          </div>
          <p class="eyebrow">Percurso de escuta</p>
          <h1>{{ data.name }}</h1>
          <p v-if="data.description" class="intro">{{ data.description }}</p>
          <div class="metrics">
            <span>{{ data.itemCount }} álbuns</span>
            <span>{{ data.listenedCount }} ouvidos nesta lista</span>
            <span>{{ progress }}% concluída</span>
          </div>
        </div>
        <div class="hero-cover">
          <img v-if="heroCover" :src="heroCover" :alt="data.items[0]?.albumTitle || data.name" />
          <span v-else>{{ data.name.slice(0, 1) }}</span>
        </div>
      </section>

      <section v-if="editing" class="edit-panel">
        <form class="metadata-form" @submit.prevent="saveMetadata">
          <p class="eyebrow">Identidade</p>
          <label>Nome <input v-model="metadata.name" required /></label>
          <label>Descrição <textarea v-model="metadata.description" rows="3" /></label>
          <div class="form-actions">
            <button type="submit" :disabled="busy">Salvar detalhes</button>
            <button class="danger" type="button" :disabled="busy" @click="deleteList">Excluir lista</button>
          </div>
        </form>

        <div class="add-panel">
          <p class="eyebrow">Adicionar do acervo</p>
          <label
            >Buscar álbum <input v-model="searchText" placeholder="Nome do álbum ou artista" @input="scheduleSearch"
          /></label>
          <p class="hint">Nesta primeira versão, a busca considera álbuns já presentes no Media Pulse.</p>
          <div v-if="searching" class="hint">Buscando...</div>
          <div class="search-results">
            <button
              v-for="album in availableResults"
              :key="album.id"
              type="button"
              :disabled="busy"
              @click="addAlbum(album.id)"
            >
              <span
                ><strong>{{ album.title }}</strong
                ><small
                  >{{ album.artistName }}<template v-if="album.year"> · {{ album.year }}</template></small
                ></span
              >
              <b>Adicionar</b>
            </button>
          </div>
        </div>
      </section>

      <section class="toolbar">
        <div>
          <p class="eyebrow">Álbuns</p>
          <h2>Ordem de escuta</h2>
        </div>
        <label class="sort-field"
          >Ordenar
          <select v-model="sortMode">
            <option value="position">Posição na lista</option>
            <option value="listened">Marcação mais recente</option>
            <option value="rating">Maior avaliação</option>
          </select>
        </label>
      </section>

      <p v-if="!displayItems.length" class="state-card">
        A lista está vazia. Abra a edição para procurar o primeiro álbum.
      </p>

      <section v-else class="album-stack">
        <article
          v-for="item in displayItems"
          :key="item.albumId"
          class="album-card"
          :class="{ listened: item.listenedAt }"
        >
          <div class="position">{{ item.position }}</div>
          <NuxtLink :to="`/music/albums/${item.albumId}`" class="cover">
            <img
              v-if="resolveMediaUrl(item.coverUrl)"
              :src="resolveMediaUrl(item.coverUrl) || ''"
              :alt="item.albumTitle"
            />
            <span v-else>{{ item.albumTitle.slice(0, 1) }}</span>
          </NuxtLink>
          <div class="album-copy">
            <p class="eyebrow">{{ item.year || 'Ano desconhecido' }}</p>
            <NuxtLink :to="`/music/albums/${item.albumId}`"
              ><h3>{{ item.albumTitle }}</h3></NuxtLink
            >
            <NuxtLink :to="`/music/artists/${item.artistId}`" class="artist">{{ item.artistName }}</NuxtLink>
            <div class="item-meta">
              <span>{{
                item.rating ? `${'★'.repeat(item.rating)}${'☆'.repeat(5 - item.rating)}` : 'Sem avaliação'
              }}</span>
              <span v-if="item.listenedAt">Marcado em {{ formatMarkedAt(item.listenedAt) }}</span>
              <span v-else>Ainda não marcado nesta lista</span>
            </div>
          </div>
          <div class="item-actions">
            <button type="button" :class="{ primary: !item.listenedAt }" :disabled="busy" @click="toggleListened(item)">
              {{ item.listenedAt ? 'Desmarcar ouvido' : 'Marcar como ouvido' }}
            </button>
            <template v-if="editing && sortMode === 'position'">
              <button
                type="button"
                :disabled="busy || item.position === 1"
                aria-label="Mover para cima"
                @click="move(item.albumId, -1)"
              >
                ↑
              </button>
              <button
                type="button"
                :disabled="busy || item.position === data.itemCount"
                aria-label="Mover para baixo"
                @click="move(item.albumId, 1)"
              >
                ↓
              </button>
              <button class="danger" type="button" :disabled="busy" @click="removeAlbum(item.albumId)">Remover</button>
            </template>
          </div>
        </article>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import type { AlbumListDetailsResponse, AlbumListItemDto, MusicSearchResponse } from '~/types/music'

const route = useRoute()
const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const slug = String(route.params.slug)
const { data, error, status } = await useAlbumListData(slug)
const editing = ref(false)
const busy = ref(false)
const searching = ref(false)
const sortMode = ref<'position' | 'listened' | 'rating'>('position')
const metadata = reactive({ name: '', description: '' })
const searchText = ref('')
const searchResults = ref<MusicSearchResponse['albums']>([])
let searchTimer: ReturnType<typeof setTimeout> | undefined

watch(
  data,
  (value) => {
    metadata.name = value?.name || ''
    metadata.description = value?.description || ''
  },
  { immediate: true },
)

const heroCover = computed(() => resolveMediaUrl(data.value?.items[0]?.coverUrl || null))
const progress = computed(() =>
  data.value?.itemCount ? Math.round((data.value.listenedCount / data.value.itemCount) * 100) : 0,
)
const availableResults = computed(() => {
  const currentIds = new Set(data.value?.items.map((item) => item.albumId) || [])
  return searchResults.value.filter((item) => !currentIds.has(item.id))
})
const displayItems = computed(() => {
  const items = [...(data.value?.items || [])]
  if (sortMode.value === 'listened') {
    return items.sort((a, b) => (b.listenedAt || '').localeCompare(a.listenedAt || '') || a.position - b.position)
  }
  if (sortMode.value === 'rating') {
    return items.sort((a, b) => (b.rating ?? -1) - (a.rating ?? -1) || a.position - b.position)
  }
  return items.sort((a, b) => a.position - b.position)
})

function formatMarkedAt(value: string) {
  return new Intl.DateTimeFormat('pt-BR', { dateStyle: 'medium' }).format(new Date(value))
}

function scheduleSearch() {
  if (searchTimer) clearTimeout(searchTimer)
  if (searchText.value.trim().length < 2) {
    searchResults.value = []
    return
  }
  searchTimer = setTimeout(searchAlbums, 300)
}

async function searchAlbums() {
  searching.value = true
  try {
    const result = await $fetch<MusicSearchResponse>('/api/music/search', {
      baseURL: config.public.apiBase,
      query: { q: searchText.value.trim(), limit: 20 },
    })
    searchResults.value = result.albums
  } finally {
    searching.value = false
  }
}

async function mutate(path: string, options: Parameters<typeof $fetch>[1]) {
  busy.value = true
  try {
    data.value = await $fetch<AlbumListDetailsResponse>(path, { baseURL: config.public.apiBase, ...options })
  } finally {
    busy.value = false
  }
}

async function saveMetadata() {
  if (!data.value) return
  await mutate(`/api/music/lists/${data.value.listId}`, { method: 'PUT', body: metadata })
}
async function addAlbum(albumId: number) {
  if (!data.value) return
  await mutate(`/api/music/lists/${data.value.listId}/albums/${albumId}`, { method: 'POST' })
}
async function removeAlbum(albumId: number) {
  if (!data.value) return
  await mutate(`/api/music/lists/${data.value.listId}/albums/${albumId}`, { method: 'DELETE' })
}
async function toggleListened(item: AlbumListItemDto) {
  if (!data.value) return
  await mutate(`/api/music/lists/${data.value.listId}/albums/${item.albumId}/listened`, {
    method: 'PATCH',
    body: { listened: !item.listenedAt },
  })
}
async function move(albumId: number, offset: number) {
  if (!data.value) return
  const ids = data.value.items.sort((a, b) => a.position - b.position).map((item) => item.albumId)
  const from = ids.indexOf(albumId)
  const to = from + offset
  if (from < 0 || to < 0 || to >= ids.length) return
  ;[ids[from], ids[to]] = [ids[to]!, ids[from]!]
  await mutate(`/api/music/lists/${data.value.listId}/order`, { method: 'PUT', body: { albumIds: ids } })
}
async function deleteList() {
  if (!data.value || !window.confirm(`Excluir a lista “${data.value.name}”? Os álbuns continuarão no acervo.`)) return
  busy.value = true
  try {
    await $fetch(`/api/music/lists/${data.value.listId}`, { baseURL: config.public.apiBase, method: 'DELETE' })
    await navigateTo('/music/lists')
  } finally {
    busy.value = false
  }
}

useHead(() => ({ title: data.value ? `${data.value.name} · Listas de álbuns` : 'Lista de álbuns · Media Pulse' }))
</script>

<style scoped>
.list-page {
  display: grid;
  gap: 42px;
  width: min(1320px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}
.hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(15rem, 0.38fr);
  gap: 28px;
  padding: clamp(24px, 5vw, 52px);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  border-radius: 40px;
  background: radial-gradient(circle at top right, rgba(230, 0, 35, 0.09), transparent 30%), #fff;
}
.hero-copy {
  display: grid;
  gap: 14px;
  align-content: end;
}
.hero-actions,
.metrics,
.form-actions,
.item-meta,
.item-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
.quiet-button,
.metrics span,
.item-meta span {
  padding: 8px 12px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.78rem;
  cursor: pointer;
}
.eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.73rem;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}
h1,
h2,
h3,
p {
  margin: 0;
}
h1 {
  font-size: clamp(3rem, 6vw, 4.38rem);
  line-height: 0.91;
  letter-spacing: -0.07em;
}
.intro {
  max-width: 45rem;
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}
.hero-cover {
  aspect-ratio: 1;
  overflow: hidden;
  border: 8px solid #fff;
  border-radius: 32px;
  background: var(--base-color-surface-warm);
}
.hero-cover img,
.cover img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.hero-cover span,
.cover span {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--base-color-text-secondary);
  font-size: 3rem;
  font-weight: 700;
}
.edit-panel {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 20px;
}
.metadata-form,
.add-panel {
  display: grid;
  gap: 14px;
  align-content: start;
  padding: 24px;
  border-radius: 28px;
  background: var(--base-color-surface-soft);
}
label {
  display: grid;
  gap: 7px;
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
  font-weight: 700;
}
input,
textarea,
select {
  padding: 11px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: #fff;
  color: inherit;
  font: inherit;
  resize: vertical;
}
button {
  padding: 9px 13px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: inherit;
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}
button.primary,
.metadata-form button[type='submit'] {
  background: var(--base-color-brand-red);
  color: #000;
  font-weight: 700;
}
button.danger {
  color: #9e0a0a;
}
button:disabled {
  cursor: default;
  opacity: 0.45;
}
.hint {
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
  line-height: 1.5;
}
.search-results {
  display: grid;
  gap: 8px;
}
.search-results button {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  text-align: left;
}
.search-results span {
  display: grid;
  gap: 3px;
}
.search-results small {
  color: var(--base-color-text-secondary);
}
.toolbar {
  display: flex;
  justify-content: space-between;
  align-items: end;
  gap: 20px;
}
.toolbar > div {
  display: grid;
  gap: 7px;
}
.toolbar h2 {
  font-size: clamp(2rem, 4vw, 3rem);
  letter-spacing: -0.05em;
}
.sort-field {
  min-width: 14rem;
}
.album-stack {
  display: grid;
  gap: 12px;
}
.album-card {
  display: grid;
  grid-template-columns: 3rem 8rem minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 14px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  border-radius: 28px;
  background: #fff;
}
.album-card.listened {
  background: color-mix(in srgb, var(--base-color-surface-soft) 72%, white);
}
.position {
  color: var(--base-color-brand-red);
  font-size: 1.5rem;
  font-weight: 700;
  text-align: center;
}
.cover {
  aspect-ratio: 1;
  overflow: hidden;
  border: 5px solid #fff;
  border-radius: 20px;
  background: var(--base-color-surface-warm);
}
.album-copy {
  display: grid;
  gap: 7px;
}
.album-copy h3 {
  color: var(--base-color-text-primary);
  font-size: 1.35rem;
  letter-spacing: -0.035em;
}
.artist {
  color: var(--base-color-text-secondary);
}
.item-meta span {
  font-size: 0.73rem;
}
.item-actions {
  max-width: 18rem;
  justify-content: end;
}
.state-card {
  padding: 24px;
  border-radius: 24px;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-secondary);
}
.error {
  color: #9e0a0a;
}
@media (max-width: 850px) {
  .hero,
  .edit-panel {
    grid-template-columns: 1fr;
  }
  .hero-cover {
    max-width: 20rem;
  }
  .album-card {
    grid-template-columns: 2rem 6rem minmax(0, 1fr);
  }
  .item-actions {
    grid-column: 2/-1;
    justify-content: start;
  }
}
@media (max-width: 560px) {
  .list-page {
    width: min(100vw - 20px, 1320px);
  }
  .toolbar {
    align-items: stretch;
    flex-direction: column;
  }
  .sort-field {
    min-width: 0;
  }
  .album-card {
    grid-template-columns: 2rem 5rem minmax(0, 1fr);
    gap: 10px;
  }
  .item-meta {
    display: grid;
  }
}
input:focus-visible,
textarea:focus-visible,
select:focus-visible {
  outline: 3px solid color-mix(in srgb, var(--base-color-focus) 66%, white);
  outline-offset: 2px;
}
</style>
