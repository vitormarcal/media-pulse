<template>
  <main class="books-page">
    <nav class="books-group-links" aria-label="Organização de livros">
      <NuxtLink to="/books/series">Séries de livros</NuxtLink><NuxtLink to="/books/universes">Universos</NuxtLink>
    </nav>
    <div v-if="editorialStatus === 'pending' || libraryStatus === 'pending'" class="state-card">
      <p>Carregando...</p>
    </div>

    <div v-else-if="editorialError || libraryError" class="state-card error">
      <p>Não foi possível carregar os livros.</p>
      <pre>{{ editorialError?.message || libraryError?.message }}</pre>
    </div>

    <template v-else-if="collectionData && libraryData">
      <template v-if="showEditorialLayer && collectionData">
        <BooksCollectionHero
          :title="collectionData.hero.title"
          :intro="collectionData.hero.intro"
          :lead="collectionData.hero.lead"
          :supporting="collectionData.hero.supporting"
        />

        <section class="books-section">
          <SectionHeading eyebrow="Em leitura" title="Abertos agora" />

          <div class="strip-grid">
            <MediaStripCard v-for="item in collectionData.inProgress" :key="item.id" :item="item" variant="large" />
          </div>
        </section>

        <BooksCollectionContext
          :eyebrow="collectionData.context.eyebrow"
          :title="collectionData.context.title"
          :description="collectionData.context.description"
          :summary="collectionData.context.summary"
          :metrics="collectionData.context.metrics"
        />

        <section id="books-finished" class="books-section">
          <SectionHeading eyebrow="Fechados" title="Últimos livros" />

          <div class="masonry-grid">
            <MediaPosterCard
              v-for="(item, index) in collectionData.recentFinishes"
              :key="item.id"
              :item="item"
              :variant="cardVariant(index)"
            />
          </div>
        </section>
      </template>

      <template v-if="libraryData">
        <BooksLibraryHero
          v-if="!showEditorialLayer"
          :title="libraryData.hero.title"
          :intro="libraryData.hero.intro"
          :back-link="libraryData.hero.backLink"
          :back-label="libraryData.hero.backLabel"
          :accent-link="libraryData.hero.accentLink"
          :accent-label="libraryData.hero.accentLabel"
          :spotlight="libraryData.hero.spotlight"
        />

        <BooksLibraryFilters
          :query="libraryData.filters.query"
          :selected-year="libraryData.filters.selectedYear"
          :years="libraryData.filters.years"
        />

        <BooksCollectionContext
          :eyebrow="libraryData.context.eyebrow"
          :title="libraryData.context.title"
          :description="libraryData.context.description"
          :summary="libraryData.context.summary"
          :metrics="libraryData.context.metrics"
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
          <button type="button" class="load-more" :disabled="loadingMore" @click="handleLoadMore">
            {{ loadingMore ? 'Buscando mais livros...' : 'Carregar mais da biblioteca' }}
          </button>
        </div>
      </template>
    </template>
  </main>
</template>

<script setup lang="ts">
import BooksCollectionContext from '~/components/books/BooksCollectionContext.vue'
import BooksCollectionHero from '~/components/books/BooksCollectionHero.vue'
import BooksLibraryFilters from '~/components/books/BooksLibraryFilters.vue'
import BooksLibraryGrid from '~/components/books/BooksLibraryGrid.vue'
import BooksLibraryHero from '~/components/books/BooksLibraryHero.vue'
import MediaPosterCard from '~/components/home/MediaPosterCard.vue'
import MediaStripCard from '~/components/home/MediaStripCard.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'
import { fetchBooksCollectionData } from '~/composables/useBooksCollectionData'
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

const showEditorialLayer = computed(() => !queryText.value && selectedYear.value == null)

const {
  data: collectionData,
  error: editorialError,
  status: editorialStatus,
} = await useAsyncData('books-collection-page', () => fetchBooksCollectionData())

const {
  data: libraryData,
  error: libraryError,
  status: libraryStatus,
} = await useAsyncData(
  'books-main-library-page',
  () => fetchBooksLibraryPageData({ q: queryText.value, year: selectedYear.value }),
  { watch: [queryText, selectedYear] },
)

const nextCursor = ref<string | null>(null)
const extraItems = ref<BookLibraryCardModel[]>([])
const loadingMore = ref(false)

watch(
  libraryData,
  (value) => {
    nextCursor.value = value?.libraryCursor ?? null
    extraItems.value = []
  },
  { immediate: true },
)

const canLoadMore = computed(() => libraryData.value?.mode === 'library' && !!nextCursor.value)

const displaySections = computed(() => {
  if (!libraryData.value) return []

  if (libraryData.value.mode !== 'library') {
    return libraryData.value.sections
  }

  return libraryData.value.sections.map((section) => {
    if (section.id === 'library-catalog') {
      return {
        ...section,
        items: [...section.items, ...extraItems.value],
      }
    }

    return section
  })
})

function cardVariant(index: number) {
  const pattern = ['feature', 'compact', 'tall', 'standard', 'compact', 'tall'] as const
  return pattern[index % pattern.length]
}

async function handleLoadMore() {
  if (!nextCursor.value || loadingMore.value) return

  loadingMore.value = true

  try {
    await appendLibraryPage(nextCursor.value)
  } finally {
    loadingMore.value = false
  }
}

async function appendLibraryPage(cursor: string) {
  const page = await fetchBooksLibraryNextPage(cursor)
  const items = buildBookLibraryCards(page.items)
  extraItems.value.push(...items)
  nextCursor.value = page.nextCursor
}

useHead(() => ({
  title:
    showEditorialLayer.value || !libraryData.value
      ? 'Livros · Media Pulse'
      : libraryData.value.mode === 'search'
        ? 'Busca de Livros · Media Pulse'
        : libraryData.value.mode === 'year'
          ? `${libraryData.value.filters.selectedYear} · Livros · Media Pulse`
          : 'Arquivo de Livros · Media Pulse',
  meta: [
    {
      name: 'description',
      content:
        showEditorialLayer.value || !libraryData.value
          ? 'Leituras em andamento, livros recentes, busca e catálogo.'
          : 'Catálogo de livros com busca e filtros por ano.',
    },
  ],
}))
</script>

<style scoped>
.books-group-links {
  display: flex;
  gap: 20px;
}
.books-group-links a {
  color: var(--base-color-text-primary);
}
.books-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.books-section,
.strip-grid {
  display: grid;
  gap: 24px;
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

.strip-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.masonry-grid {
  column-count: 4;
  column-gap: 20px;
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

@media (max-width: 1280px) {
  .masonry-grid {
    column-count: 3;
  }
}

@media (max-width: 900px) {
  .strip-grid {
    grid-template-columns: 1fr;
  }

  .masonry-grid {
    column-count: 2;
  }
}

@media (max-width: 720px) {
  .books-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}

@media (max-width: 520px) {
  .masonry-grid {
    column-count: 1;
  }
}
</style>
