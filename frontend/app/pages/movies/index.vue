<template>
  <main class="movies-page movies-browse">
    <header class="browse-header">
      <h1>Filmes</h1>
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
    <MoviesLibraryFilters
      :query="queryText"
      :selected-year="selectedYear"
      :selected-unwatched="selectedUnwatched"
      :years="years"
    />

    <section v-if="showRecent" class="browse-recent" aria-labelledby="recent-heading">
      <h2 id="recent-heading">Últimos filmes vistos</h2>
      <p class="section-note">Última sessão registrada de cada filme.</p>
      <p v-if="recentStatus === 'pending'" role="status">Carregando filmes recentes…</p>
      <p v-else-if="recentError" role="alert">
        Não foi possível carregar os filmes recentes.
        <button type="button" @click="refreshRecent()">Tentar novamente</button>
      </p>
      <ol v-else-if="recent?.items.length" class="recent-grid">
        <li v-for="movie in recent.items" :key="movie.movieId">
          <component
            :is="movie.slug ? NuxtLink : 'div'"
            :to="movie.slug ? `/movies/${movie.slug}` : undefined"
            class="recent-item"
          >
            <MediaImage :src="resolveMediaUrl(movie.coverUrl)" :alt="movie.title" loading="lazy" />
            <div>
              <h3>{{ movie.title }}</h3>
              <p v-if="movie.watchedAt">{{ formatRelativeDate(movie.watchedAt) }}</p>
            </div>
          </component>
        </li>
      </ol>
      <p v-else>Nenhuma sessão registrada.</p>
    </section>

    <section class="browse-catalog" aria-live="polite" :aria-busy="catalogStatus === 'pending'">
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
          eyebrow=""
          :title="catalogTitle"
          description=""
          summary=""
          :items="catalogItems"
          :empty-message="emptyMessage"
        />
        <p v-if="owner.authenticated && queryText" class="add-hint">
          Não encontrou o filme? <button type="button" @click="openAdd">Adicionar ao acervo</button>
        </p>
        <div v-if="nextCursor" class="load-more-row">
          <p v-if="moreError" role="alert">
            Não foi possível carregar mais filmes. Os itens anteriores foram preservados.
          </p>
          <button type="button" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? 'Carregando…' : moreError ? 'Tentar carregar mais' : 'Carregar mais filmes' }}
          </button>
        </div>
      </template>
    </section>

    <details class="browse-panel history-summary">
      <summary>Resumo do histórico{{ selectedYear ? ` · ${selectedYear}` : '' }}</summary>
      <template v-if="selectedYear && !queryText">
        <dl v-if="catalog?.yearStats && !catalogError && catalogStatus !== 'pending'" class="summary-metrics">
          <div>
            <dt>Filmes vistos no ano</dt>
            <dd>{{ catalog.yearStats.uniqueMoviesCount }}</dd>
          </div>
          <div>
            <dt>Sessões no ano</dt>
            <dd>{{ catalog.yearStats.watchesCount }}</dd>
          </div>
          <div>
            <dt>Revisitas no ano</dt>
            <dd>{{ catalog.yearStats.rewatchesCount }}</dd>
          </div>
        </dl>
        <p v-else>O resumo anual estará disponível ao carregar os filmes do ano.</p>
      </template>
      <template v-else>
        <p v-if="statsStatus === 'pending'" role="status">Carregando resumo…</p>
        <p v-else-if="statsError" role="alert">
          Não foi possível carregar o resumo. <button type="button" @click="refreshStats()">Tentar novamente</button>
        </p>
        <template v-else-if="stats">
          <p class="section-note">Todo o acervo e histórico registrado, independentemente da busca.</p>
          <dl class="summary-metrics">
            <div>
              <dt>Filmes no acervo</dt>
              <dd>{{ stats.total.uniqueMoviesCount }}</dd>
            </div>
            <div>
              <dt>Sessões registradas</dt>
              <dd>{{ stats.total.watchesCount }}</dd>
            </div>
            <div>
              <dt>Sem sessão registrada</dt>
              <dd>{{ stats.unwatchedCount }}</dd>
            </div>
          </dl>
          <p v-if="stats.firstWatchAt && stats.latestWatchAt" class="section-note">
            De {{ formatAbsoluteDate(stats.firstWatchAt) }} a {{ formatAbsoluteDate(stats.latestWatchAt) }}.
          </p>
        </template>
      </template>
    </details>
    <p v-if="statsError" class="section-note">
      Os anos do histórico estão indisponíveis. <button type="button" @click="refreshStats()">Tentar novamente</button>
    </p>
  </main>
</template>
<script setup lang="ts">
import { NuxtLink } from '#components'
import MoviesLibraryFilters from '~/components/movies/MoviesLibraryFilters.vue'
import MoviesLibraryGrid from '~/components/movies/MoviesLibraryGrid.vue'
import MovieManualAddCard from '~/components/movies/MovieManualAddCard.vue'
import { fetchMoviesBrowsePage, fetchMoviesLibraryNextPage } from '~/composables/useMoviesLibraryData'
import { movieBrowseCards } from '~/utils/movieBrowse'
import { formatAbsoluteDate, formatRelativeDate } from '~/utils/formatting'
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
  status: statsStatus,
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
  $fetch<MoviesRecentResponse>('/api/movies/recent', { baseURL: config.public.apiBase, query: { limit: 6 } }),
)
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
  if (!nextCursor.value || loadingMore.value) return
  const currentGeneration = generation
  loadingMore.value = true
  moreError.value = false
  try {
    const page = await fetchMoviesLibraryNextPage(nextCursor.value, selectedUnwatched.value)
    if (generation !== currentGeneration) return
    const existing = new Set(catalogItems.value.map((item) => item.movieId))
    extraItems.value.push(...movieBrowseCards(page.items).filter((item) => !existing.has(item.movieId)))
    nextCursor.value = page.nextCursor
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
.browse-header,
.browse-panel {
  padding: clamp(20px, 3vw, 32px);
  border-radius: 28px;
  border: 1px solid var(--base-color-border);
  background: white;
}
.browse-header {
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
.browse-recent {
  display: grid;
  gap: 16px;
}
.section-note {
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.5;
}
.recent-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 20px;
  list-style: none;
  padding: 0;
  margin: 0;
}
.recent-item {
  display: grid;
  grid-template-columns: 64px minmax(0, 1fr);
  align-items: center;
  gap: 12px;
}
.recent-item :deep(img) {
  width: 64px;
  height: 96px;
  object-fit: contain;
  border-radius: 12px;
}
.recent-item :deep(.media-image-fallback) {
  padding: 4px;
  min-height: 80px;
  font-size: 0.75rem;
}
.recent-item h3 {
  margin: 0;
  font-size: 1rem;
  line-height: 1.3;
  overflow-wrap: anywhere;
}
.recent-item p {
  margin: 6px 0 0;
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
  gap: 12px;
}
.load-more-row p {
  margin: 0;
  color: var(--base-color-text-secondary);
}
summary {
  cursor: pointer;
  font-weight: 600;
}
.summary-metrics {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 24px;
  margin: 24px 0;
}
dt {
  color: var(--base-color-text-secondary);
}
dd {
  margin: 8px 0 0;
  font-size: 2rem;
  font-weight: 600;
}
.history-summary > .section-note {
  margin-top: 20px;
}
@media (max-width: 900px) {
  .recent-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 600px) {
  .movies-page {
    width: calc(100% - 24px);
    padding-top: 20px;
  }
  .recent-grid,
  .summary-metrics {
    grid-template-columns: 1fr;
  }
  .browse-header {
    align-items: start;
  }
}
</style>
