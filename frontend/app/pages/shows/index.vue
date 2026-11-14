<template>
  <main class="shows-page shows-browse">
    <header v-if="!showRecent" class="browse-header">
      <h1>Séries</h1>
      <nav aria-label="Acervo de séries">
        <NuxtLink to="/shows/lists">Listas</NuxtLink>
        <button
          v-if="owner.authenticated"
          id="shows-add-toggle"
          type="button"
          :aria-expanded="adding"
          aria-controls="show-add"
          @click="toggleAdd"
        >
          {{ adding ? 'Fechar cadastro' : 'Adicionar série' }}
        </button>
      </nav>
    </header>
    <ShowsCollectionHero
      v-if="showRecent"
      browse
      :title="editorial.title"
      intro=""
      :lead="editorial.lead"
      :supporting="editorial.supporting"
      :lead-placeholder="leadPlaceholder"
    >
      <template #actions>
        <nav aria-label="Acervo de séries">
          <NuxtLink to="/shows/lists">Listas</NuxtLink>
          <button
            v-if="owner.authenticated"
            id="shows-add-toggle"
            type="button"
            :aria-expanded="adding"
            aria-controls="show-add"
            @click="toggleAdd"
          >
            Adicionar série
          </button>
        </nav>
      </template>
      <template #search>
        <p v-if="editorial.hasCurrent" class="section-note">
          Em andamento: episódios cadastrados parcialmente vistos, com atividade nos últimos 120 dias.
        </p>
        <p v-if="currentError" class="section-note" role="alert">
          Não foi possível carregar as séries em andamento.
          <button type="button" @click="refreshCurrent()">Tentar novamente</button>
        </p>
        <p v-if="recentError" class="section-note" role="alert">
          Não foi possível carregar a atividade recente.
          <button type="button" @click="refreshRecent()">Tentar novamente</button>
        </p>
        <ShowsLibraryFilters
          mode="search"
          :query="queryText"
          :selected-year="selectedYear"
          :selected-unwatched="selectedUnwatched"
          :years="years"
        />
      </template>
    </ShowsCollectionHero>
    <section v-if="owner.authenticated && addOpened" v-show="adding" id="show-add" class="browse-panel">
      <ShowManualAddCard :initial-title="queryText" />
    </section>
    <section class="browse-catalog" :aria-busy="catalogStatus === 'pending' || loadingMore">
      <ShowsLibraryFilters
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
      <p v-if="catalogStatus === 'pending'" role="status">Carregando séries…</p>
      <p v-else-if="catalogError" class="browse-panel" role="alert">
        Não foi possível carregar o acervo. <button type="button" @click="refreshCatalog()">Tentar novamente</button>
      </p>
      <template v-else-if="catalog">
        <p v-if="catalog.truncated" class="section-note">
          {{
            queryText
              ? 'A busca retorna até 40 correspondências antes do filtro de episódios registrados. Refine o título para encontrar outras séries.'
              : `Exibindo ${catalog.items.length} de ${catalog.yearStats?.uniqueShowsCount} séries com visualização neste ano.`
          }}
        </p>
        <ShowsLibraryGrid
          refined
          eyebrow=""
          :title="catalogTitle"
          description=""
          :summary="catalogContext"
          :items="catalogItems"
          :empty-message="emptyMessage"
        />
        <p v-if="owner.authenticated && queryText" class="section-note">
          Não encontrou a série? <button type="button" @click="openAdd">Adicionar ao acervo</button>
        </p>
        <div v-if="nextCursor" ref="pageSentinel" class="load-more-row">
          <p v-if="loadingMore" role="status">Carregando mais séries…</p>
          <template v-else-if="moreError"
            ><p role="alert">Não foi possível carregar mais séries. Os itens anteriores foram preservados.</p>
            <button type="button" @click="loadMore">Tentar novamente</button></template
          >
        </div>
      </template>
    </section>
  </main>
</template>
<script setup lang="ts">
import { NuxtLink } from '#components'
import ShowsLibraryFilters from '~/components/shows/ShowsLibraryFilters.vue'
import ShowsLibraryGrid from '~/components/shows/ShowsLibraryGrid.vue'
import ShowManualAddCard from '~/components/shows/ShowManualAddCard.vue'
import { fetchShowsBrowsePage, fetchShowsLibraryNextPage } from '~/composables/useShowsLibraryData'
import { showBrowseCards, buildShowBrowseHero } from '~/utils/showBrowse'
import type { ShowBrowseCard } from '~/utils/showBrowse'
import ShowsCollectionHero from '~/components/shows/ShowsCollectionHero.vue'
import type { ShowsStatsResponse } from '~/types/shows'
import type { ShowsRecentResponse, CurrentlyWatchingShowDto } from '~/types/home'
const owner = useOwnerAuth()
const route = useRoute()
const config = useRuntimeConfig()
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
async function toggleAdd() {
  await navigateTo({ path: '/shows', query: { ...route.query, add: adding.value ? undefined : '1' } })
  await nextTick()
  document.getElementById('shows-add-toggle')?.focus({ preventScroll: true })
}
async function openAdd() {
  await navigateTo({ path: '/shows', query: { ...route.query, add: '1' } })
  document.getElementById('show-add')?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
const {
  data: stats,
  error: statsError,
  refresh: refreshStats,
} = await useAsyncData('shows-browse-stats', () =>
  $fetch<ShowsStatsResponse>('/api/shows/stats', { baseURL: config.public.apiBase }),
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
  'shows-browse-catalog',
  () => fetchShowsBrowsePage({ q: queryText.value, year: selectedYear.value, unwatched: selectedUnwatched.value }),
  { watch: [filterKey] },
)
const showRecent = computed(() => !queryText.value && !selectedYear.value && !selectedUnwatched.value && !adding.value)
const {
  data: recent,
  status: recentStatus,
  error: recentError,
  refresh: refreshRecent,
} = await useAsyncData('shows-browse-recent', () =>
  $fetch<ShowsRecentResponse>('/api/shows/recent', { baseURL: config.public.apiBase, query: { limit: 18 } }),
)
const {
  data: current,
  status: currentStatus,
  error: currentError,
  refresh: refreshCurrent,
} = await useAsyncData('shows-browse-current', () =>
  $fetch<CurrentlyWatchingShowDto[]>('/api/shows/currently-watching', {
    baseURL: config.public.apiBase,
    query: { limit: 12, activeWithinDays: 120 },
  }),
)
const editorial = computed(() =>
  buildShowBrowseHero(
    currentError.value ? [] : (current.value ?? []),
    recentError.value ? [] : (recent.value?.items ?? []),
  ),
)
const leadPlaceholder = computed(() =>
  currentStatus.value === 'pending' || recentStatus.value === 'pending'
    ? 'Carregando séries…'
    : currentError.value || recentError.value
      ? 'Destaque indisponível'
      : 'Sem atividade registrada ainda',
)
const pageSentinel = ref<HTMLElement | null>(null)
let pageObserver: IntersectionObserver | null = null
const extraItems = ref<ShowBrowseCard[]>([])
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
const catalogItems = computed(() => [
  ...new Map([...(catalog.value?.items ?? []), ...extraItems.value].map((item) => [item.showId, item])).values(),
])
const catalogTitle = computed(() =>
  queryText.value
    ? `Resultados para “${queryText.value}”`
    : selectedYear.value
      ? `Vistas em ${selectedYear.value}`
      : selectedUnwatched.value
        ? 'Sem episódio registrado'
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
      ? `${countLabel(totals.uniqueShowsCount, 'série', 'séries')} · ${countLabel(totals.watchesCount, 'registro de visualização', 'registros de visualização')}`
      : ''
  }
  if (!stats.value || statsError.value) return ''
  return countLabel(
    selectedUnwatched.value
      ? stats.value.unwatchedCount
      : stats.value.total.uniqueShowsCount + stats.value.unwatchedCount,
    'série',
    'séries',
  )
})
const emptyMessage = computed(() =>
  queryText.value
    ? 'Nenhuma série encontrada nesta busca.'
    : selectedYear.value
      ? 'Nenhuma visualização registrada neste ano.'
      : selectedUnwatched.value
        ? 'Nenhuma série sem episódio registrado.'
        : 'Ainda não há séries no acervo.',
)
async function loadMore() {
  if (!nextCursor.value || loadingMore.value || catalogStatus.value !== 'success' || catalogError.value) return
  const currentGeneration = generation
  const requestedCursor = nextCursor.value
  loadingMore.value = true
  moreError.value = false
  try {
    const page = await fetchShowsLibraryNextPage(requestedCursor, selectedUnwatched.value)
    if (generation !== currentGeneration) return
    const existing = new Set(catalogItems.value.map((item) => item.showId))
    extraItems.value.push(
      ...showBrowseCards(page.items).filter((item) => {
        if (existing.has(item.showId)) return false
        existing.add(item.showId)
        return true
      }),
    )
    nextCursor.value = page.nextCursor === requestedCursor ? null : page.nextCursor
  } catch {
    if (generation === currentGeneration) moreError.value = true
  } finally {
    if (generation === currentGeneration) loadingMore.value = false
  }
}
useHead({
  title: 'Séries · Media Pulse',
  meta: [{ name: 'description', content: 'Acervo de séries, progresso e histórico de visualizações.' }],
})
</script>
<style scoped>
.shows-page {
  display: grid;
  grid-template-columns: minmax(0, 1fr);
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}
.browse-header {
  display: flex;
  align-items: center;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 20px;
}
.browse-header h1 {
  margin: 0;
  font-size: clamp(2rem, 4vw, 3rem);
  letter-spacing: -0.05em;
}
nav {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}
nav a,
button {
  padding: 8px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  cursor: pointer;
  font: inherit;
}
nav button {
  background: var(--base-color-brand-red);
  color: black;
}
.browse-panel {
  padding: clamp(20px, 2vw, 28px);
  border-radius: 28px;
  border: 1px solid var(--base-color-border);
  background: white;
}
.browse-catalog {
  display: grid;
  gap: 20px;
  min-width: 0;
}
.section-note {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.9rem;
  line-height: 1.5;
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
@media (max-width: 720px) {
  .shows-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
