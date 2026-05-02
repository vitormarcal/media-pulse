<template>
  <main class="movies-library-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a biblioteca de filmes...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível montar a biblioteca de filmes com os dados atuais.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <MoviesLibraryHero
        :title="data.hero.title"
        :intro="data.hero.intro"
        :back-link="data.hero.backLink"
        :back-label="data.hero.backLabel"
        :accent-link="data.hero.accentLink"
        :accent-label="data.hero.accentLabel"
        :utility-link="data.hero.utilityLink"
        :utility-label="data.hero.utilityLabel"
        :spotlight="data.hero.spotlight"
      />

      <MoviesLibraryFilters
        :query="data.filters.query"
        :selected-year="data.filters.selectedYear"
        :years="data.filters.years"
      />

      <section v-if="data.mode === 'library'" class="manual-lists-hint">
        <div class="manual-lists-hint__copy">
          <p class="manual-lists-hint__eyebrow">Curadoria manual</p>
          <h2>Prefere entrar por recortes?</h2>
          <p>
            As listas manuais funcionam como pequenas estantes temáticas para quando a biblioteca inteira é ampla demais
            e você quer começar por afinidade, ocasião ou humor.
          </p>
        </div>

        <NuxtLink class="manual-lists-hint__action" to="/movies/lists"> Ver listas manuais </NuxtLink>
      </section>

      <MovieManualAddCard v-if="showManualAddCard" :initial-title="queryText" />

      <section v-else-if="showManualEntryHint" class="manual-entry-hint">
        <div class="manual-entry-hint__copy">
          <p class="manual-entry-hint__eyebrow">Entrada manual</p>
          <h2>Não era o filme certo?</h2>
          <p>
            A busca da biblioteca serve para reencontrar o que já existe. Se você quer trazer um título novo, pode abrir
            a entrada manual sem depender de zero resultados.
          </p>
        </div>

        <NuxtLink class="manual-entry-hint__action" :to="manualAddLink"> Adicionar filme </NuxtLink>
      </section>

      <MoviesCollectionContext
        :eyebrow="data.context.eyebrow"
        :title="data.context.title"
        :description="data.context.description"
        :summary="data.context.summary"
        :metrics="data.context.metrics"
      />

      <MoviesLibraryGrid
        v-for="section in displaySections"
        :key="section.id"
        :eyebrow="section.eyebrow"
        :title="section.title"
        :description="section.description"
        :summary="section.summary"
        :items="section.items"
        :empty-message="section.emptyMessage"
      />

      <div v-if="canLoadMore" class="load-more-row">
        <button type="button" class="load-more" :disabled="loadingMore || hydratingDormant" @click="handleLoadMore">
          {{ loadingMore || hydratingDormant ? 'Buscando mais filmes...' : 'Carregar mais da biblioteca' }}
        </button>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import MoviesCollectionContext from '~/components/movies/MoviesCollectionContext.vue'
import MoviesLibraryFilters from '~/components/movies/MoviesLibraryFilters.vue'
import MoviesLibraryGrid from '~/components/movies/MoviesLibraryGrid.vue'
import MoviesLibraryHero from '~/components/movies/MoviesLibraryHero.vue'
import MovieManualAddCard from '~/components/movies/MovieManualAddCard.vue'
import { fetchMoviesLibraryNextPage, fetchMoviesLibraryPageData } from '~/composables/useMoviesLibraryData'
import type { MovieLibraryCardModel } from '~/types/movies'
import { buildMovieLibraryCards } from '~/utils/movies'

const route = useRoute()

const queryText = computed(() => {
  const value = route.query.q
  return typeof value === 'string' ? value.trim() : ''
})

const selectedYear = computed<number | null>(() => {
  const value = route.query.year
  if (typeof value !== 'string' || !value.trim()) return null

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
})

const addMode = computed(() => {
  const value = route.query.add
  return value === '1' || value === 'true'
})

const { data, error, status } = await useAsyncData(
  'movies-library-page',
  () => fetchMoviesLibraryPageData({ q: queryText.value, year: selectedYear.value }),
  {
    watch: [queryText, selectedYear],
  },
)

const nextCursor = ref<string | null>(null)
const extraActiveItems = ref<MovieLibraryCardModel[]>([])
const extraDormantItems = ref<MovieLibraryCardModel[]>([])
const loadingMore = ref(false)
const hydratingDormant = ref(false)

watch(
  data,
  (value) => {
    nextCursor.value = value?.libraryCursor ?? null
    extraActiveItems.value = []
    extraDormantItems.value = []

    if (value?.mode === 'library') {
      void prefetchDormantPreview()
    }
  },
  { immediate: true },
)

const canLoadMore = computed(() => data.value?.mode === 'library' && !!nextCursor.value)

const displaySections = computed(() => {
  if (!data.value) return []

  if (data.value.mode !== 'library') {
    return data.value.sections
  }

  return data.value.sections.map((section) => {
    if (section.id === 'active-library') {
      return {
        ...section,
        items: [...section.items, ...extraActiveItems.value],
      }
    }

    if (section.id === 'dormant-library') {
      return {
        ...section,
        items: [...section.items, ...extraDormantItems.value],
      }
    }

    return section
  })
})

const showManualAddCard = computed(() => {
  if (addMode.value) return true
  if (!queryText.value || !data.value || data.value.mode !== 'search') return false

  const resultsSection = data.value.sections.find((section) => section.id === 'search-results')
  return (resultsSection?.items.length ?? 0) === 0
})

const showManualEntryHint = computed(() => {
  if (showManualAddCard.value || !queryText.value || !data.value || data.value.mode !== 'search') {
    return false
  }

  const resultsSection = data.value.sections.find((section) => section.id === 'search-results')
  return (resultsSection?.items.length ?? 0) > 0
})

const manualAddLink = computed(() =>
  queryText.value ? `/movies/library?q=${encodeURIComponent(queryText.value)}&add=1` : '/movies/library?add=1',
)

async function handleLoadMore() {
  if (!nextCursor.value || loadingMore.value || hydratingDormant.value) return

  loadingMore.value = true

  try {
    await appendLibraryPage(nextCursor.value)
  } finally {
    loadingMore.value = false
  }
}

function dormantItemsCount() {
  const baseDormant =
    data.value?.mode === 'library'
      ? (data.value.sections.find((section) => section.id === 'dormant-library')?.items.length ?? 0)
      : 0

  return baseDormant + extraDormantItems.value.length
}

async function appendLibraryPage(cursor: string) {
  const page = await fetchMoviesLibraryNextPage(cursor)
  const items = buildMovieLibraryCards(page.items)
  extraActiveItems.value.push(...items.filter((item) => !item.isDormant))
  extraDormantItems.value.push(...items.filter((item) => item.isDormant))
  nextCursor.value = page.nextCursor
}

async function prefetchDormantPreview() {
  if (
    !data.value ||
    data.value.mode !== 'library' ||
    hydratingDormant.value ||
    dormantItemsCount() > 0 ||
    !nextCursor.value
  ) {
    return
  }

  hydratingDormant.value = true

  try {
    let pagesFetched = 0

    while (nextCursor.value && dormantItemsCount() === 0 && pagesFetched < 6) {
      await appendLibraryPage(nextCursor.value)
      pagesFetched += 1
    }
  } finally {
    hydratingDormant.value = false
  }
}

useHead(() => ({
  title: 'Biblioteca de Filmes · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Arquivo completo dos filmes no Media Pulse, com busca, recortes e navegação editorial.',
    },
  ],
}))
</script>

<style scoped>
.movies-library-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.load-more-row {
  display: flex;
  justify-content: center;
}

.manual-lists-hint,
.manual-entry-hint {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 24px;
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 48%, white);
}

.manual-lists-hint__copy,
.manual-entry-hint__copy {
  display: grid;
  gap: 8px;
}

.manual-lists-hint__eyebrow,
.manual-entry-hint__eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.manual-lists-hint__copy h2,
.manual-lists-hint__copy p,
.manual-entry-hint__copy h2,
.manual-entry-hint__copy p {
  margin: 0;
}

.manual-lists-hint__copy h2,
.manual-entry-hint__copy h2 {
  font-size: clamp(1.65rem, 3vw, 2.2rem);
  line-height: 0.98;
  letter-spacing: -0.045em;
}

.manual-lists-hint__copy p,
.manual-entry-hint__copy p {
  color: var(--base-color-text-secondary);
  line-height: 1.56;
}

.manual-lists-hint__action,
.manual-entry-hint__action {
  padding: 10px 16px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  white-space: nowrap;
}

.load-more {
  padding: 10px 18px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  cursor: pointer;
}

.load-more:disabled {
  opacity: 0.7;
  cursor: default;
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

@media (max-width: 720px) {
  .movies-library-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }

  .manual-lists-hint,
  .manual-entry-hint {
    grid-template-columns: 1fr;
  }
}
</style>
