<template>
  <main class="books-library-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a biblioteca de livros...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível montar a biblioteca de livros com os dados atuais.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <BooksLibraryHero
        :title="data.hero.title"
        :intro="data.hero.intro"
        :back-link="data.hero.backLink"
        :back-label="data.hero.backLabel"
        :accent-link="data.hero.accentLink"
        :accent-label="data.hero.accentLabel"
        :spotlight="data.hero.spotlight"
      />

      <BooksLibraryFilters
        :query="data.filters.query"
        :selected-year="data.filters.selectedYear"
        :years="data.filters.years"
      />

      <BooksCollectionContext
        :eyebrow="data.context.eyebrow"
        :title="data.context.title"
        :description="data.context.description"
        :summary="data.context.summary"
        :metrics="data.context.metrics"
      />

      <BooksLibraryGrid
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
          {{ loadingMore || hydratingDormant ? 'Buscando mais livros...' : 'Carregar mais da biblioteca' }}
        </button>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import BooksCollectionContext from '~/components/books/BooksCollectionContext.vue'
import BooksLibraryFilters from '~/components/books/BooksLibraryFilters.vue'
import BooksLibraryGrid from '~/components/books/BooksLibraryGrid.vue'
import BooksLibraryHero from '~/components/books/BooksLibraryHero.vue'
import { fetchBooksLibraryNextPage, fetchBooksLibraryPageData } from '~/composables/useBooksLibraryData'
import type { BookLibraryCardModel } from '~/types/books'
import { buildBookLibraryCards } from '~/utils/books'

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
  'books-library-page',
  () => fetchBooksLibraryPageData({ q: queryText.value, year: selectedYear.value }),
  {
    watch: [queryText, selectedYear],
  },
)

const nextCursor = ref<string | null>(null)
const extraActiveItems = ref<BookLibraryCardModel[]>([])
const extraDormantItems = ref<BookLibraryCardModel[]>([])
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
  const page = await fetchBooksLibraryNextPage(cursor)
  const items = buildBookLibraryCards(page.items)
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
  title: 'Biblioteca de Livros · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Arquivo completo dos livros no Media Pulse, com busca, recortes anuais e navegação editorial.',
    },
  ],
}))
</script>

<style scoped>
.books-library-page {
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
  .books-library-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
