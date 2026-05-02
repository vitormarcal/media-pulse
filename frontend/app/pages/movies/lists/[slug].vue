<template>
  <main class="list-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página da lista...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar esta lista.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <section class="list-hero-shell" :style="heroShellStyle">
        <div class="list-hero">
          <div class="copy">
            <div class="topbar">
              <NuxtLink class="back-link" to="/movies/lists"> Voltar para listas </NuxtLink>
              <button
                type="button"
                class="edit-order-button"
                :class="{ active: reorderMode }"
                @click="toggleReorderMode"
              >
                {{ reorderMode ? 'Fechar ajustes' : 'Ajustar ordem' }}
              </button>
            </div>

            <p class="eyebrow">Lista manual</p>
            <h1>{{ data.name }}</h1>
            <p v-if="heroIntro" class="intro">{{ heroIntro }}</p>

            <div class="meta-list">
              <span v-for="item in data.heroMeta" :key="item" class="meta-pill">{{ item }}</span>
            </div>
          </div>

          <component :is="spotlightWrapper" :to="spotlightMovie?.href || undefined" class="spotlight-link">
            <article class="spotlight-card">
              <div class="spotlight-poster">
                <img v-if="spotlightImageUrl" :src="spotlightImageUrl" :alt="spotlightMovie?.title || data.name" />
                <div v-else class="spotlight-fallback">{{ (spotlightMovie?.title || data.name).slice(0, 1) }}</div>
              </div>

              <div class="spotlight-body">
                <p class="spotlight-kicker">Primeiro da ordem</p>
                <h2>{{ spotlightMovie?.title || data.name }}</h2>
                <p v-if="spotlightMovie" class="spotlight-meta">{{ spotlightMovie.sessionsLabel }}</p>
              </div>
            </article>
          </component>
        </div>
      </section>

      <section v-if="reorderMode && data.movies.length" class="cover-panel">
        <div class="cover-panel__copy">
          <p class="eyebrow">Imagem do recorte</p>
          <h2>Escolha a capa da lista</h2>
          <p class="cover-panel__description">Sem escolha manual, a capa segue o primeiro filme da ordem.</p>
        </div>

        <div class="cover-panel__toolbar">
          <p class="cover-panel__status">
            {{
              coverDraftMovieId
                ? `Capa fixa em ${selectedCoverMovie?.title || 'um filme desta lista'}.`
                : 'Sem capa fixa. O primeiro filme da ordem segue como imagem padrão.'
            }}
          </p>
          <button
            type="button"
            class="cover-panel__reset"
            :disabled="savingCover || coverDraftMovieId == null"
            @click="updateCoverMovie(null)"
          >
            {{ savingCover && coverPendingMovieId == null ? 'Salvando...' : 'Usar primeiro da ordem' }}
          </button>
        </div>

        <div class="cover-grid">
          <button
            v-for="movie in orderedMovies"
            :key="`cover-${movie.id}`"
            type="button"
            class="cover-option"
            :class="{ 'cover-option--active': coverDraftMovieId === movie.movieId }"
            :disabled="savingCover"
            @click="updateCoverMovie(movie.movieId)"
          >
            <div class="cover-option__poster">
              <img v-if="resolveMediaUrl(movie.imageUrl)" :src="resolveMediaUrl(movie.imageUrl)" :alt="movie.title" />
              <div v-else class="cover-option__fallback">{{ movie.title.slice(0, 1) }}</div>
            </div>
            <div class="cover-option__copy">
              <strong>{{ movie.title }}</strong>
              <p>{{ movie.subtitle }}</p>
              <span>{{ coverDraftMovieId === movie.movieId ? 'Capa atual' : 'Usar como capa' }}</span>
            </div>
          </button>
        </div>
      </section>

      <section v-if="reorderMode && data.movies.length" class="order-panel">
        <div class="order-copy">
          <p class="eyebrow">Sequência manual</p>
          <h2>Ajuste a ordem do recorte</h2>
          <p class="order-description">Arraste para remontar a sequência da lista.</p>
        </div>

        <div class="order-toolbar">
          <p class="order-status">
            {{ orderDirty ? 'Há uma nova ordem pronta para salvar.' : 'A ordem atual já está salva.' }}
          </p>
          <div class="order-toolbar__actions">
            <button
              type="button"
              class="order-toolbar__button"
              :disabled="savingOrder || !orderDirty"
              @click="resetOrder"
            >
              Desfazer
            </button>
            <button
              type="button"
              class="order-toolbar__button order-toolbar__button--primary"
              :disabled="savingOrder || !orderDirty"
              @click="saveOrder"
            >
              {{ savingOrder ? 'Salvando...' : 'Salvar ordem' }}
            </button>
          </div>
        </div>

        <div class="order-stack">
          <article
            v-for="(movie, index) in orderedMovies"
            :key="movie.id"
            class="order-card"
            :class="{
              'order-card--dragging': draggingMovieId === movie.movieId,
              'order-card--drop-target': dropTargetMovieId === movie.movieId,
            }"
            :draggable="savingOrder ? 'false' : 'true'"
            @dragstart="handleDragStart(movie.movieId)"
            @dragend="handleDragEnd"
            @dragover.prevent="handleDragOver(movie.movieId)"
            @drop.prevent="handleDrop(movie.movieId)"
          >
            <div class="order-card__poster">
              <img v-if="resolveMediaUrl(movie.imageUrl)" :src="resolveMediaUrl(movie.imageUrl)" :alt="movie.title" />
              <div v-else class="order-card__fallback">{{ movie.title.slice(0, 1) }}</div>
            </div>

            <div class="order-card__copy">
              <p class="order-card__index">Posição {{ index + 1 }}</p>
              <strong>{{ movie.title }}</strong>
              <p>{{ movie.subtitle }}</p>
            </div>

            <div class="order-card__actions">
              <span class="drag-hint">Arraste para mover</span>
            </div>
          </article>
        </div>
      </section>

      <MoviesLibraryGrid
        eyebrow="Recorte"
        :title="gridTitle"
        :description="gridDescription"
        :summary="gridSummary"
        :items="displayMovies"
        layout="masonry"
        empty-message="Nenhum filme entrou nesta lista ainda."
      />
    </template>
  </main>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import MoviesLibraryGrid from '~/components/movies/MoviesLibraryGrid.vue'
import { useMovieListPageData } from '~/composables/useMovieListPageData'
import type { MovieLibraryCardModel, MovieListCoverUpdateRequest, MovieListOrderUpdateRequest } from '~/types/movies'

const route = useRoute()
const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const slug = computed(() => String(route.params.slug))
const reorderMode = ref(false)
const orderedMovies = ref<MovieLibraryCardModel[]>([])
const draggingMovieId = ref<number | null>(null)
const dropTargetMovieId = ref<number | null>(null)
const savingOrder = ref(false)
const savingCover = ref(false)
const coverPendingMovieId = ref<number | null>(null)
const coverDraftMovieId = ref<number | null>(null)

const { data, error, status, refresh } = await useMovieListPageData(slug.value)

watch(
  data,
  (value) => {
    orderedMovies.value = value?.movies.map((movie) => ({ ...movie })) ?? []
    coverDraftMovieId.value = value?.coverMovieId ?? null
  },
  { immediate: true },
)

const displayMovies = computed(() => (orderedMovies.value.length ? orderedMovies.value : (data.value?.movies ?? [])))
const spotlightMovie = computed(() => displayMovies.value[0] ?? null)
const selectedCoverMovie = computed(
  () => displayMovies.value.find((movie) => movie.movieId === coverDraftMovieId.value) ?? null,
)
const heroCoverImageUrl = computed(() =>
  resolveMediaUrl(
    selectedCoverMovie.value?.imageUrl ?? data.value?.coverImageUrl ?? spotlightMovie.value?.imageUrl ?? null,
  ),
)
const spotlightImageUrl = computed(() => resolveMediaUrl(spotlightMovie.value?.imageUrl ?? null))
const heroShellStyle = computed(() =>
  heroCoverImageUrl.value
    ? {
        backgroundImage: `linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(246, 243, 238, 0.97)), radial-gradient(circle at top right, rgba(230, 0, 35, 0.1), transparent 28%), url("${heroCoverImageUrl.value}")`,
      }
    : undefined,
)
const spotlightWrapper = computed(() => (spotlightMovie.value?.href ? NuxtLink : 'div'))
const heroIntro = computed(() => {
  if (!data.value) return ''
  return data.value.description || ''
})
const gridTitle = computed(() => (data.value ? `Filmes de ${data.value.name}` : 'Os filmes desta lista'))
const gridDescription = computed(() => '')
const gridSummary = computed(() =>
  data.value
    ? `${data.value.stats.movieCount} filmes no recorte e ${data.value.stats.watchedMoviesCount} com sessão registrada.`
    : 'Uma nova porta de entrada para a biblioteca.',
)
const persistedMovieIds = computed(() => data.value?.movies.map((movie) => movie.movieId) ?? [])
const orderedMovieIds = computed(() => orderedMovies.value.map((movie) => movie.movieId))
const orderDirty = computed(() => persistedMovieIds.value.join(',') !== orderedMovieIds.value.join(','))

function toggleReorderMode() {
  reorderMode.value = !reorderMode.value
  if (!reorderMode.value) {
    resetOrder()
  }
}

function resetOrder() {
  orderedMovies.value = data.value?.movies.map((movie) => ({ ...movie })) ?? []
  draggingMovieId.value = null
  dropTargetMovieId.value = null
}

function handleDragStart(movieId: number) {
  if (savingOrder.value) return
  draggingMovieId.value = movieId
  dropTargetMovieId.value = movieId
}

function handleDragOver(movieId: number) {
  if (!draggingMovieId.value || draggingMovieId.value === movieId) return
  dropTargetMovieId.value = movieId
}

function handleDrop(targetMovieId: number) {
  if (!draggingMovieId.value || draggingMovieId.value === targetMovieId) {
    handleDragEnd()
    return
  }

  const nextItems = [...orderedMovies.value]
  const sourceIndex = nextItems.findIndex((movie) => movie.movieId === draggingMovieId.value)
  const targetIndex = nextItems.findIndex((movie) => movie.movieId === targetMovieId)
  if (sourceIndex < 0 || targetIndex < 0) {
    handleDragEnd()
    return
  }

  const [moved] = nextItems.splice(sourceIndex, 1)
  nextItems.splice(targetIndex, 0, moved)
  orderedMovies.value = nextItems
  handleDragEnd()
}

function handleDragEnd() {
  draggingMovieId.value = null
  dropTargetMovieId.value = null
}

async function saveOrder() {
  if (!data.value || savingOrder.value || !orderDirty.value) return

  savingOrder.value = true
  try {
    await $fetch(`/api/movies/lists/${data.value.listId}/order`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: {
        movieIds: orderedMovieIds.value,
      } satisfies MovieListOrderUpdateRequest,
    })
    await refresh()
  } finally {
    savingOrder.value = false
  }
}

async function updateCoverMovie(movieId: number | null) {
  if (!data.value || savingCover.value || coverDraftMovieId.value === movieId) return

  const previousMovieId = coverDraftMovieId.value
  coverDraftMovieId.value = movieId
  coverPendingMovieId.value = movieId
  savingCover.value = true

  try {
    await $fetch(`/api/movies/lists/${data.value.listId}/cover`, {
      baseURL: config.public.apiBase,
      method: 'PATCH',
      body: {
        coverMovieId: movieId,
      } satisfies MovieListCoverUpdateRequest,
    })
    await refresh()
  } catch {
    coverDraftMovieId.value = previousMovieId
  } finally {
    savingCover.value = false
    coverPendingMovieId.value = null
  }
}

useHead(() => ({
  title: data.value ? `${data.value.name} · Filmes · Media Pulse` : 'Lista · Filmes · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value
        ? `Filmes ligados à lista ${data.value.name} no Media Pulse.`
        : 'Página interna de lista manual de filmes no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.list-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.state-card {
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--base-color-text-secondary);
}

.state-card.error {
  color: #7a1414;
}

pre {
  margin: 12px 0 0;
  white-space: pre-wrap;
}

.list-hero-shell {
  padding: clamp(24px, 4vw, 36px);
  border-radius: 40px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.98)),
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 28%);
  background-size: cover;
  background-position: center;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
}

.list-hero {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(0, 0.9fr) minmax(20rem, 1.1fr);
  align-items: end;
}

.copy {
  display: grid;
  gap: 12px;
  align-content: end;
}

.topbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.back-link {
  width: fit-content;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.edit-order-button {
  border: 0;
  padding: 8px 12px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.58);
  color: var(--base-color-text-secondary);
  font: inherit;
  font-size: 0.76rem;
  cursor: pointer;
}

.edit-order-button.active {
  background: color-mix(in srgb, var(--base-color-surface-warm) 88%, white);
  color: var(--base-color-text-primary);
}

.eyebrow,
.spotlight-kicker {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

h1 {
  margin: 0;
  font-size: clamp(3rem, 7vw, 5.8rem);
  line-height: 0.92;
  letter-spacing: -0.075em;
}

.intro {
  max-width: 42rem;
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.62;
}

.meta-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 2px;
}

.meta-pill {
  padding: 8px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.spotlight-link {
  display: block;
}

.spotlight-card {
  display: grid;
  grid-template-columns: minmax(13rem, 0.92fr) minmax(0, 1fr);
  gap: 20px;
  padding: clamp(18px, 3vw, 28px);
  border-radius: 40px;
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(246, 243, 238, 0.96));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 52%, white);
  backdrop-filter: blur(6px);
}

.spotlight-poster {
  aspect-ratio: 0.76;
  overflow: hidden;
  border-radius: 28px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.spotlight-poster img,
.spotlight-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.spotlight-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-weight: 700;
  font-size: 3rem;
}

.spotlight-body {
  display: grid;
  align-content: end;
  gap: 8px;
}

h2,
.spotlight-subtitle,
.spotlight-meta,
.spotlight-note {
  margin: 0;
}

h2 {
  font-size: clamp(1.9rem, 3.4vw, 3rem);
  line-height: 0.98;
  letter-spacing: -0.04em;
}

.spotlight-subtitle,
.spotlight-meta {
  color: var(--base-color-text-secondary);
}

.spotlight-note {
  color: var(--base-color-text-muted);
  font-size: 0.88rem;
}

.cover-panel,
.order-panel,
.order-copy,
.order-stack,
.cover-panel__copy {
  display: grid;
  gap: 20px;
}

.cover-panel__description,
.order-description,
.order-card__copy p,
.order-card__index,
.order-status,
.drag-hint,
.cover-panel__status,
.cover-option__copy p,
.cover-option__copy span {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.cover-panel,
.order-panel {
  padding: 24px;
  border-radius: 32px;
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.06), transparent 32%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 52%, white);
}

.cover-panel__description {
  line-height: 1.58;
}

.cover-panel__toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.cover-panel__status {
  font-size: 0.88rem;
}

.cover-panel__reset {
  border: 0;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}

.cover-grid {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(14rem, 1fr));
  gap: 14px;
}

.cover-option {
  display: grid;
  gap: 14px;
  padding: 12px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 52%, white);
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.86);
  text-align: left;
  cursor: pointer;
}

.cover-option--active {
  border-color: color-mix(in srgb, var(--base-color-brand-red) 38%, white);
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(249, 241, 235, 0.98));
}

.cover-option__poster {
  aspect-ratio: 1.12;
  overflow: hidden;
  border-radius: 20px;
  border: 6px solid #fff;
  background: var(--base-color-surface-soft);
}

.cover-option__poster img,
.cover-option__fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-option__fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-size: 2rem;
  font-weight: 700;
}

.cover-option__copy {
  display: grid;
  gap: 6px;
}

.cover-option__copy strong,
.cover-option__copy span {
  line-height: 1.2;
}

.cover-option__copy span {
  font-size: 0.76rem;
}

.order-description {
  line-height: 1.58;
}

.order-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.order-status {
  font-size: 0.88rem;
}

.order-toolbar__actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.order-toolbar__button {
  border: 0;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.78rem;
  cursor: pointer;
}

.order-toolbar__button--primary {
  background: var(--base-color-brand-red);
  color: #fff;
}

.order-toolbar__button:disabled {
  cursor: default;
  opacity: 0.55;
}

.order-stack {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.order-card {
  display: grid;
  grid-template-columns: 5.5rem minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 14px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.84);
  cursor: grab;
}

.order-card--dragging {
  opacity: 0.48;
}

.order-card--drop-target {
  outline: 2px solid color-mix(in srgb, var(--base-color-brand-red) 55%, white);
  outline-offset: 2px;
}

.order-card__poster {
  overflow: hidden;
  aspect-ratio: 0.78;
  border-radius: 20px;
  border: 6px solid #fff;
  background: var(--base-color-surface-soft);
}

.order-card__poster img,
.order-card__fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.order-card__fallback {
  display: grid;
  place-items: center;
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--base-color-text-secondary);
}

.order-card__copy {
  display: grid;
  gap: 6px;
}

.order-card__copy strong {
  font-size: 1rem;
  line-height: 1.2;
}

.order-card__index {
  font-size: 0.76rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.order-card__actions {
  display: grid;
  justify-items: end;
}

.drag-hint {
  padding: 8px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  font-size: 0.76rem;
  font-weight: 700;
}

@media (max-width: 980px) {
  .list-hero {
    grid-template-columns: 1fr;
  }

  .order-stack {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .list-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }

  .topbar,
  .order-card {
    grid-template-columns: 1fr;
  }

  .topbar {
    display: grid;
    justify-content: start;
  }

  .order-card__actions {
    justify-items: start;
  }
}
</style>
