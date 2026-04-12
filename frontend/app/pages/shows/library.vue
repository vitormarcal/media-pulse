<template>
  <main class="shows-library-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a biblioteca de séries...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível montar a biblioteca de séries com os dados atuais.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <ShowsLibraryHero
        :title="data.hero.title"
        :intro="data.hero.intro"
        :back-link="data.hero.backLink"
        :back-label="data.hero.backLabel"
        :accent-link="data.hero.accentLink"
        :accent-label="data.hero.accentLabel"
        :spotlight="data.hero.spotlight"
      />

      <ShowsLibraryFilters
        :query="data.filters.query"
        :selected-year="data.filters.selectedYear"
        :years="data.filters.years"
      />

      <ShowsCollectionContext
        :eyebrow="data.context.eyebrow"
        :title="data.context.title"
        :description="data.context.description"
        :summary="data.context.summary"
        :metrics="data.context.metrics"
      />

      <ShowsLibraryGrid
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
        <button type="button" class="load-more" :disabled="loadingMore" @click="handleLoadMore">
          {{ loadingMore ? 'Buscando mais séries...' : 'Carregar mais da biblioteca' }}
        </button>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import ShowsCollectionContext from '~/components/shows/ShowsCollectionContext.vue'
import ShowsLibraryFilters from '~/components/shows/ShowsLibraryFilters.vue'
import ShowsLibraryGrid from '~/components/shows/ShowsLibraryGrid.vue'
import ShowsLibraryHero from '~/components/shows/ShowsLibraryHero.vue'
import { fetchShowsLibraryNextPage, fetchShowsLibraryPageData } from '~/composables/useShowsLibraryData'
import type { ShowLibraryCardModel } from '~/types/shows'
import { buildShowLibraryCards } from '~/utils/shows'

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

const { data, error, status } = await useAsyncData(
  'shows-library-page',
  () => fetchShowsLibraryPageData({ q: queryText.value, year: selectedYear.value }),
  {
    watch: [queryText, selectedYear],
  },
)

const nextCursor = ref<string | null>(null)
const extraActiveItems = ref<ShowLibraryCardModel[]>([])
const extraDormantItems = ref<ShowLibraryCardModel[]>([])
const loadingMore = ref(false)

watch(data, (value) => {
  nextCursor.value = value?.libraryCursor ?? null
  extraActiveItems.value = []
  extraDormantItems.value = []
}, { immediate: true })

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

async function handleLoadMore() {
  if (!nextCursor.value || loadingMore.value) return

  loadingMore.value = true

  try {
    const page = await fetchShowsLibraryNextPage(nextCursor.value)
    const items = buildShowLibraryCards(page.items)
    extraActiveItems.value.push(...items.filter(item => !item.isDormant))
    extraDormantItems.value.push(...items.filter(item => item.isDormant))
    nextCursor.value = page.nextCursor
  } finally {
    loadingMore.value = false
  }
}

useHead(() => ({
  title: 'Biblioteca de Séries · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Arquivo completo das séries no Media Pulse, com busca, recortes e navegação editorial.',
    },
  ],
}))
</script>

<style scoped>
.shows-library-page {
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
  .shows-library-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
