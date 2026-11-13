<template>
  <main class="movies-page movies-browse">
    <header class="browse-header">
      <h1 v-if="!showRecent">Filmes</h1>
      <nav aria-label="Acervo de filmes">
        <NuxtLink to="/movies/lists">Listas</NuxtLink>
        <NuxtLink to="/movies/collections">Coleções</NuxtLink>
        <button
          v-if="owner.authenticated"
          type="button"
          :aria-expanded="adding"
          aria-controls="movie-add"
          @click="toggleAdd"
        >
          {{ adding ? 'Fechar cadastro' : 'Adicionar filme' }}
        </button>
      </nav>
    </header>
    <section v-if="owner.authenticated && addOpened" v-show="adding" id="movie-add" class="browse-panel">
      <MovieManualAddCard :initial-title="queryText" />
    </section>

    <section v-if="showRecent" class="editorial-panel" aria-label="Último filme visto e busca">
      <img
        v-if="latestPoster && !recentError"
        class="editorial-backdrop"
        :src="resolveMediaUrl(latestPoster) || undefined"
        alt=""
        aria-hidden="true"
        @error="latestImageFailed"
      />
      <div class="editorial-layout">
        <div class="editorial-intro">
          <p>Filmes</p>
          <h1>Filmes recentes</h1>
        </div>
        <p v-if="recentStatus === 'pending'" role="status">Carregando filmes recentes…</p>
        <p v-else-if="recentError" role="alert">
          Não foi possível carregar os filmes recentes.
          <button type="button" @click="refreshRecent()">Tentar novamente</button>
        </p>
        <component
          :is="latestMovie?.slug ? NuxtLink : 'article'"
          v-else-if="latestMovie"
          :to="latestMovie.slug ? `/movies/${latestMovie.slug}` : undefined"
          class="latest-movie"
          :class="{ 'has-poster': latestPoster }"
        >
          <div class="latest-copy">
            <p class="latest-label">Último filme visto</p>
            <h2>{{ latestMovie.title }}</h2>
            <p v-if="latestMovie.year" class="latest-year">{{ latestMovie.year }}</p>
            <p v-if="latestMovie.watchedAt" class="latest-date">
              Última sessão {{ formatRelativeDate(latestMovie.watchedAt) }}
            </p>
          </div>
          <div v-if="latestPoster" class="latest-poster">
            <img
              :src="resolveMediaUrl(latestPoster) || undefined"
              :alt="latestMovie.title"
              @error="latestImageFailed"
            />
          </div>
        </component>
        <p v-else>Nenhuma sessão registrada.</p>
      </div>
      <ol
        v-if="otherRecent.length && !recentError"
        class="recent-grid"
        tabindex="0"
        aria-label="Outros filmes recentes"
      >
        <li v-for="movie in otherRecent" :key="movie.movieId">
          <component
            :is="movie.slug ? NuxtLink : 'div'"
            :to="movie.slug ? `/movies/${movie.slug}` : undefined"
            class="recent-item"
          >
            <div class="recent-poster">
              <MediaImage :src="resolveMediaUrl(movie.coverUrl)" :alt="movie.title" loading="lazy" />
            </div>
            <div>
              <h3>{{ movie.title }}</h3>
              <p v-if="movie.watchedAt">{{ formatRelativeDate(movie.watchedAt) }}</p>
            </div>
          </component>
        </li>
      </ol>
      <div class="editorial-search">
        <MoviesLibraryFilters
          mode="search"
          :query="queryText"
          :selected-year="selectedYear"
          :selected-unwatched="selectedUnwatched"
          :years="years"
        />
      </div>
    </section>
    <section class="browse-catalog" :aria-busy="catalogStatus === 'pending' || loadingMore">
      <div class="catalog-controls">
        <MoviesLibraryFilters
          :mode="showRecent ? 'filters' : 'all'"
          :query="queryText"
          :selected-year="selectedYear"
          :selected-unwatched="selectedUnwatched"
          :years="years"
        />
        <p v-if="statsError" class="section-note" role="alert">
          Os totais e os anos do histórico estão indisponíveis.
          <button type="button" @click="refreshStats()">Tentar novamente</button>
        </p>
      </div>
      <p v-if="catalogStatus === 'pending'" role="status">Carregando filmes…</p>
      <p v-else-if="catalogError" class="browse-panel" role="alert">
        Não foi possível carregar o acervo. <button type="button" @click="refreshCatalog()">Tentar novamente</button>
      </p>
      <template v-else-if="catalog">
        <p v-if="catalog.truncated" class="section-note">
          {{
            queryText
              ? 'A busca retorna até 40 correspondências do acervo. Refine o título para encontrar outros filmes.'
              : `Exibindo ${catalog.items.length} de ${catalog.yearStats?.uniqueMoviesCount} filmes com sessão neste ano.`
          }}
        </p>
        <MoviesLibraryGrid
          refined
          layout="aligned"
          eyebrow=""
          :title="catalogTitle"
          description=""
          :summary="catalogContext"
          :items="catalogItems"
          :empty-message="emptyMessage"
        />
        <p v-if="owner.authenticated && queryText" class="add-hint">
          Não encontrou o filme? <button type="button" @click="openAdd">Adicionar ao acervo</button>
        </p>
        <div v-if="nextCursor" ref="pageSentinel" class="load-more-row">
          <p v-if="loadingMore" role="status">Carregando mais filmes…</p>
          <template v-else-if="moreError">
            <p role="alert">Não foi possível carregar mais filmes. Os itens anteriores foram preservados.</p>
            <button type="button" @click="loadMore">Tentar novamente</button>
          </template>
        </div>
      </template>
    </section>
  </main>
</template>
<script setup lang="ts">
import { NuxtLink } from '#components'
import MoviesLibraryFilters from '~/components/movies/MoviesLibraryFilters.vue'
import MoviesLibraryGrid from '~/components/movies/MoviesLibraryGrid.vue'
import MovieManualAddCard from '~/components/movies/MovieManualAddCard.vue'
import { fetchMoviesBrowsePage, fetchMoviesLibraryNextPage } from '~/composables/useMoviesLibraryData'
import { movieBrowseCards } from '~/utils/movieBrowse'
import { formatRelativeDate } from '~/utils/formatting'
import type { MovieLibraryCardModel, MoviesStatsResponse } from '~/types/movies'
import type { MoviesRecentResponse } from '~/types/home'
const owner = useOwnerAuth()
const route = useRoute()
const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const queryText = computed(() => (typeof route.query.q === 'string' ? route.query.q.trim() : ''))
const selectedUnwatched = computed(() => ['1', 'true'].includes(String(route.query.unwatched)))
const selectedYear = computed(() => {
  if (queryText.value || selectedUnwatched.value) return null
  const year = Number(route.query.year)
  return Number.isInteger(year) && year >= 1900 && year <= new Date().getUTCFullYear() + 1 ? year : null
})
const filterKey = computed(() => JSON.stringify([queryText.value, selectedYear.value, selectedUnwatched.value]))
const adding = computed(() => owner.value.authenticated && ['1', 'true'].includes(String(route.query.add)))
const addOpened = ref(adding.value)
watch(adding, (value) => {
  if (value) addOpened.value = true
})
function toggleAdd() {
  navigateTo({ path: '/movies', query: { ...route.query, add: adding.value ? undefined : '1' } })
}
async function openAdd() {
  await navigateTo({ path: '/movies', query: { ...route.query, add: '1' } })
  document.getElementById('movie-add')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
const {
  data: stats,
  error: statsError,
  refresh: refreshStats,
} = await useAsyncData('movies-browse-stats', () =>
  $fetch<MoviesStatsResponse>('/api/movies/stats', { baseURL: config.public.apiBase }),
)
const years = computed(() => {
  const values = new Set((stats.value?.years ?? []).map((item) => item.year))
  if (selectedYear.value) values.add(selectedYear.value)
  return [...values].sort((a, b) => b - a).map((year) => ({ year, label: String(year), watches: '' }))
})
const {
  data: catalog,
  status: catalogStatus,
  error: catalogError,
  refresh: refreshCatalog,
} = await useAsyncData(
  'movies-browse-catalog',
  () => fetchMoviesBrowsePage({ q: queryText.value, year: selectedYear.value, unwatched: selectedUnwatched.value }),
  { watch: [filterKey] },
)
const showRecent = computed(() => !queryText.value && !selectedYear.value && !selectedUnwatched.value && !adding.value)
const {
  data: recent,
  status: recentStatus,
  error: recentError,
  refresh: refreshRecent,
} = await useAsyncData('movies-browse-recent', () =>
  $fetch<MoviesRecentResponse>('/api/movies/recent', { baseURL: config.public.apiBase, query: { limit: 5 } }),
)
const latestMovie = computed(() => recent.value?.items[0] ?? null)
const failedLatestPoster = ref<string | null>(null)
const latestPoster = computed(() => {
  const url = latestMovie.value?.coverUrl
  return url && url !== failedLatestPoster.value ? url : null
})
function latestImageFailed(event: Event) {
  const url = latestMovie.value?.coverUrl
  const failedUrl = (event.target as HTMLImageElement).getAttribute('src')
  if (url && failedUrl === resolveMediaUrl(url)) failedLatestPoster.value = url
}
const otherRecent = computed(() => recent.value?.items.slice(1, 5) ?? [])
const pageSentinel = ref<HTMLElement | null>(null)
let pageObserver: IntersectionObserver | null = null
const extraItems = ref<MovieLibraryCardModel[]>([])
const nextCursor = ref<string | null>(null)
const loadingMore = ref(false)
const moreError = ref(false)
let generation = 0
watch(
  filterKey,
  () => {
    generation++
    extraItems.value = []
    nextCursor.value = null
    loadingMore.value = false
    moreError.value = false
  },
  { flush: 'sync' },
)
watch(
  catalog,
  (value) => {
    generation++
    extraItems.value = []
    nextCursor.value = value?.nextCursor ?? null
    loadingMore.value = false
    moreError.value = false
  },
  { immediate: true },
)
watch(
  [pageSentinel, nextCursor, loadingMore, moreError, catalogStatus],
  ([element, cursor, busy, failed, status]) => {
    pageObserver?.disconnect()
    if (!element || !cursor || busy || failed || status !== 'success') return
    pageObserver = new IntersectionObserver(
      (entries) => {
        if (entries.some((entry) => entry.isIntersecting) && !moreError.value) void loadMore()
      },
      { rootMargin: '0px 0px 600px 0px' },
    )
    pageObserver.observe(element)
  },
  { flush: 'post' },
)
onBeforeUnmount(() => pageObserver?.disconnect())
const catalogItems = computed(() => [...(catalog.value?.items ?? []), ...extraItems.value])
const catalogTitle = computed(() =>
  queryText.value
    ? `Resultados para “${queryText.value}”`
    : selectedYear.value
      ? `Vistos em ${selectedYear.value}`
      : selectedUnwatched.value
        ? 'Sem sessão registrada'
        : 'Acervo',
)
function countLabel(count: number, singular: string, plural: string) {
  return `${count} ${count === 1 ? singular : plural}`
}
const catalogContext = computed(() => {
  if (queryText.value) return ''
  if (selectedYear.value) {
    const totals = catalog.value?.yearStats
    return totals
      ? `${countLabel(totals.uniqueMoviesCount, 'filme', 'filmes')} · ${countLabel(totals.watchesCount, 'sessão', 'sessões')}`
      : ''
  }
  if (!stats.value || statsError.value) return ''
  return countLabel(
    selectedUnwatched.value ? stats.value.unwatchedCount : stats.value.total.uniqueMoviesCount,
    'filme',
    'filmes',
  )
})
const emptyMessage = computed(() =>
  queryText.value
    ? 'Nenhum filme encontrado nesta busca.'
    : selectedYear.value
      ? 'Nenhuma sessão registrada neste ano.'
      : selectedUnwatched.value
        ? 'Nenhum filme sem sessão registrada.'
        : 'Ainda não há filmes no acervo.',
)
async function loadMore() {
  if (!nextCursor.value || loadingMore.value || catalogStatus.value !== 'success' || catalogError.value) return
  const currentGeneration = generation
  const requestedCursor = nextCursor.value
  loadingMore.value = true
  moreError.value = false
  try {
    const page = await fetchMoviesLibraryNextPage(requestedCursor, selectedUnwatched.value)
    if (generation !== currentGeneration) return
    const existing = new Set(catalogItems.value.map((item) => item.movieId))
    extraItems.value.push(...movieBrowseCards(page.items).filter((item) => !existing.has(item.movieId)))
    nextCursor.value = page.nextCursor === requestedCursor ? null : page.nextCursor
  } catch {
    if (generation === currentGeneration) moreError.value = true
  } finally {
    if (generation === currentGeneration) loadingMore.value = false
  }
}
useHead({
  title: 'Filmes · Media Pulse',
  meta: [{ name: 'description', content: 'Acervo de filmes e histórico de sessões.' }],
})
</script>
<style scoped>
.movies-page {
  display: grid;
  gap: 32px;
  width: min(1480px, calc(100% - 40px));
  margin: 0 auto;
  padding: 28px 0 64px;
}
.editorial-panel,
.browse-panel {
  padding: clamp(20px, 2vw, 28px);
  border-radius: 28px;
  border: 1px solid var(--base-color-border);
  background: white;
}
.browse-header {
  min-height: 44px;
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 20px;
}
h1 {
  margin: 0;
  font-size: clamp(2rem, 4vw, 3rem);
  letter-spacing: -0.05em;
  line-height: 1.1;
}
h2 {
  margin: 0;
  font-size: 1.75rem;
  letter-spacing: -0.04em;
}
nav {
  margin-left: auto;
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
nav a,
button {
  padding: 10px 16px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  cursor: pointer;
  font: inherit;
}
.browse-header button {
  background: var(--base-color-brand-red);
  color: black;
}
button:disabled {
  opacity: 0.65;
  cursor: default;
}
.section-note {
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.5;
}
.editorial-panel {
  display: grid;
  gap: 24px;
  position: relative;
  isolation: isolate;
  overflow: hidden;
  padding: clamp(20px, 3vw, 36px);
  border-radius: 40px;
  background: var(--base-color-surface-soft);
}
.editorial-backdrop {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.08;
  pointer-events: none;
}
.editorial-panel > :not(.editorial-backdrop) {
  position: relative;
  z-index: 1;
}
.editorial-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  align-items: end;
  gap: 24px;
}
.editorial-intro {
  display: grid;
  gap: 12px;
  min-width: 0;
}
.editorial-intro p {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
  font-weight: 700;
}
.editorial-intro h1 {
  font-size: clamp(3.2rem, 7vw, 6rem);
  line-height: 0.95;
  letter-spacing: -0.065em;
  max-width: 12ch;
  overflow-wrap: anywhere;
}
.editorial-search {
  padding-top: 24px;
  border-top: 1px solid var(--base-color-border);
}
.latest-movie {
  align-self: start;
  display: grid;
  gap: 20px;
  padding: 20px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.94);
  align-items: center;
  min-width: 0;
}
.latest-movie.has-poster {
  grid-template-columns: minmax(0, 0.95fr) minmax(0, 1fr);
}
.latest-copy {
  min-width: 0;
  max-width: 34rem;
}
.has-poster .latest-copy {
  grid-column: 2;
  grid-row: 1;
  align-self: center;
}
.latest-copy p {
  margin: 0;
}
.latest-label {
  color: var(--base-color-text-secondary);
  font-size: 0.85rem;
  font-weight: 600;
}
.latest-copy h2 {
  margin: 16px 0;
  font-size: clamp(1.75rem, 2.5vw, 2.5rem);
  line-height: 1.08;
  letter-spacing: -0.04em;
  overflow-wrap: anywhere;
}
.latest-year,
.latest-date {
  color: var(--base-color-text-secondary);
}
.latest-copy .latest-date {
  margin-top: 12px;
  font-size: 0.9rem;
}
.latest-poster {
  display: grid;
  justify-items: center;
  grid-column: 1;
  grid-row: 1;
}
.latest-poster img {
  display: block;
  width: auto;
  max-width: 100%;
  height: auto;
  max-height: 420px;
  border-radius: 20px;
}
.catalog-controls {
  display: grid;
  gap: 16px;
}
.recent-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  align-content: start;
  list-style: none;
  padding: 4px;
  margin: -4px;
  min-width: 0;
}
.recent-grid:focus-visible {
  outline: 2px solid var(--base-color-focus);
  outline-offset: 2px;
  border-radius: 12px;
}
.recent-grid li {
  min-width: 0;
}
.recent-item {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  align-content: start;
  gap: 12px;
}
.recent-poster {
  border: 6px solid white;
  box-sizing: border-box;
  aspect-ratio: 2 / 3;
  display: grid;
  place-items: center;
  border-radius: 24px;
  overflow: hidden;
  background: var(--base-color-surface-soft);
}
.recent-poster :deep(img) {
  display: block;
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.recent-poster :deep(.media-image-fallback) {
  width: 100%;
  height: 100%;
  padding: 4px;
  box-sizing: border-box;
  display: grid;
  place-items: center;
  font-size: 0.75rem;
}
.recent-item > div:last-child {
  min-width: 0;
}
.recent-item h3 {
  margin: 0;
  white-space: normal;
  font-size: 1.125rem;
  line-height: 1.2;
  letter-spacing: -0.02em;
  overflow-wrap: anywhere;
}
.recent-item p {
  margin: 8px 0 0;
  color: var(--base-color-text-secondary);
  font-size: 0.85rem;
}
.browse-catalog {
  display: grid;
  gap: 20px;
  min-width: 0;
}
.add-hint {
  margin: 0;
  color: var(--base-color-text-secondary);
}
.load-more-row {
  display: grid;
  justify-items: center;
  align-content: center;
  min-height: 48px;
  gap: 12px;
}
.load-more-row p {
  margin: 0;
  color: var(--base-color-text-secondary);
}
@media (max-width: 1100px) {
  .latest-movie.has-poster {
    grid-template-columns: minmax(0, 0.95fr) minmax(0, 1fr);
  }
  .latest-poster img {
    width: auto;
    max-height: 420px;
  }
  .editorial-layout {
    grid-template-columns: minmax(0, 1fr);
  }
  .recent-grid {
    grid-template-columns: none;
    grid-auto-flow: column;
    grid-auto-columns: clamp(160px, 36vw, 240px);
    overflow-x: auto;
    overscroll-behavior-x: contain;
    scroll-snap-type: x proximity;
    scroll-padding-inline: 4px;
  }
  .recent-grid li {
    scroll-snap-align: start;
  }
}
@media (max-width: 600px) {
  .movies-page {
    width: calc(100% - 24px);
    padding-top: 20px;
  }
  .latest-movie,
  .latest-movie.has-poster {
    grid-template-columns: minmax(0, 1fr);
    gap: 20px;
    padding: 16px;
  }
  .has-poster .latest-copy {
    grid-column: 1;
    grid-row: 2;
  }
  .latest-poster img {
    max-height: 340px;
  }
  .latest-copy h2 {
    font-size: 1.75rem;
    margin: 12px 0;
  }
  .editorial-intro h1 {
    font-size: clamp(2.8rem, 15vw, 4.2rem);
  }
  .editorial-panel {
    gap: 20px;
    border-radius: 28px;
  }
  .editorial-search {
    padding-top: 20px;
  }
  .browse-header {
    align-items: start;
  }
}
</style>
