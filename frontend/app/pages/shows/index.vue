<template>
  <main class="shows-page">
    <div v-if="editorialStatus === 'pending' || libraryStatus === 'pending'" class="state-card">
      <p>Montando a página de séries...</p>
    </div>

    <div v-else-if="editorialError || libraryError" class="state-card error">
      <p>Não foi possível montar a página de séries com os dados atuais.</p>
      <pre>{{ editorialError?.message || libraryError?.message }}</pre>
    </div>

    <template v-else-if="editorialData && libraryData">
      <template v-if="showEditorialLayer">
        <ShowsCollectionHero
          :title="editorialData.hero.title"
          :intro="editorialData.hero.intro"
          :lead="editorialData.hero.lead"
          :supporting="editorialData.hero.supporting"
          accent-link="/shows?add=1"
          accent-label="Adicionar série"
        />

        <section class="shows-section">
          <SectionHeading eyebrow="Em curso" title="As séries que ainda seguem abertas" description="" summary="" />

          <div class="strip-grid">
            <MediaStripCard v-for="item in editorialData.inProgress" :key="item.id" :item="item" variant="large" />
          </div>
        </section>

        <ShowsCollectionContext
          :eyebrow="editorialData.context.eyebrow"
          :title="editorialData.context.title"
          :description="editorialData.context.description"
          :summary="editorialData.context.summary"
          :metrics="editorialData.context.metrics"
        />

        <section class="shows-section">
          <SectionHeading eyebrow="Mais recente" title="O que acabou de passar pela tela" description="" summary="" />

          <div class="masonry-grid">
            <MediaPosterCard
              v-for="(item, index) in editorialData.recentMoments"
              :key="item.id"
              :item="item"
              :variant="cardVariant(index)"
            />
          </div>
        </section>
      </template>

      <ShowsLibraryHero
        v-else
        :title="libraryData.hero.title"
        :intro="libraryData.hero.intro"
        :back-link="libraryData.hero.backLink"
        :back-label="libraryData.hero.backLabel"
        :accent-link="libraryData.hero.accentLink"
        :accent-label="libraryData.hero.accentLabel"
        :spotlight="libraryData.hero.spotlight"
      />

      <ShowsLibraryFilters
        :query="libraryData.filters.query"
        :selected-year="libraryData.filters.selectedYear"
        :selected-unwatched="libraryData.filters.selectedUnwatched"
        :years="libraryData.filters.years"
      />

      <ShowManualAddCard v-if="showManualAddCard" :initial-title="queryText" />

      <section v-else-if="showManualEntryHint" class="entry-hint">
        <div class="entry-hint__copy">
          <p class="entry-hint__eyebrow">Entrada manual</p>
          <h2>Não era a série certa?</h2>
        </div>

        <NuxtLink class="entry-hint__action" :to="manualAddLink">Adicionar série</NuxtLink>
      </section>

      <ShowsCollectionContext
        :eyebrow="libraryData.context.eyebrow"
        :title="libraryData.context.title"
        :description="libraryData.context.description"
        :summary="libraryData.context.summary"
        :metrics="libraryData.context.metrics"
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
          {{ loadingMore ? 'Buscando mais séries...' : 'Carregar mais séries' }}
        </button>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import MediaPosterCard from '~/components/home/MediaPosterCard.vue'
import MediaStripCard from '~/components/home/MediaStripCard.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'
import ShowManualAddCard from '~/components/shows/ShowManualAddCard.vue'
import ShowsCollectionContext from '~/components/shows/ShowsCollectionContext.vue'
import ShowsCollectionHero from '~/components/shows/ShowsCollectionHero.vue'
import ShowsLibraryFilters from '~/components/shows/ShowsLibraryFilters.vue'
import ShowsLibraryGrid from '~/components/shows/ShowsLibraryGrid.vue'
import ShowsLibraryHero from '~/components/shows/ShowsLibraryHero.vue'
import { useShowsCollectionData } from '~/composables/useShowsCollectionData'
import { fetchShowsLibraryNextPage, fetchShowsLibraryPageData } from '~/composables/useShowsLibraryData'
import type { ShowLibraryCardModel } from '~/types/shows'
import { buildShowLibraryCards } from '~/utils/shows'

const route = useRoute()

const queryText = computed(() => {
  const value = route.query.q
  return typeof value === 'string' ? value.trim() : ''
})

const selectedUnwatched = computed(() => {
  const value = route.query.unwatched
  return value === '1' || value === 'true'
})

const selectedYear = computed<number | null>(() => {
  if (selectedUnwatched.value) return null
  const value = route.query.year
  if (typeof value !== 'string' || !value.trim()) return null

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
})

const addMode = computed(() => {
  const value = route.query.add
  return value === '1' || value === 'true'
})

const { data: editorialData, error: editorialError, status: editorialStatus } = await useShowsCollectionData()
const {
  data: libraryData,
  error: libraryError,
  status: libraryStatus,
} = await useAsyncData(
  'shows-main-library-page',
  () => fetchShowsLibraryPageData({ q: queryText.value, year: selectedYear.value, unwatched: selectedUnwatched.value }),
  {
    watch: [queryText, selectedYear, selectedUnwatched],
  },
)

const nextCursor = ref<string | null>(null)
const extraLibraryItems = ref<ShowLibraryCardModel[]>([])
const loadingMore = ref(false)

watch(
  libraryData,
  (value) => {
    nextCursor.value = value?.libraryCursor ?? null
    extraLibraryItems.value = []
  },
  { immediate: true },
)

const showEditorialLayer = computed(
  () => !queryText.value && selectedYear.value == null && !selectedUnwatched.value && !addMode.value,
)
const canLoadMore = computed(() => libraryData.value?.mode === 'library' && !!nextCursor.value)

const displaySections = computed(() => {
  if (!libraryData.value) return []

  if (libraryData.value.mode !== 'library') {
    return libraryData.value.sections.map((section) => ({
      ...section,
      description: '',
      summary: '',
    }))
  }

  return libraryData.value.sections.map((section) => {
    if (section.id === 'library-catalog') {
      return {
        ...section,
        description: '',
        summary: '',
        items: [...section.items, ...extraLibraryItems.value],
      }
    }

    return {
      ...section,
      description: '',
      summary: '',
    }
  })
})

const showManualAddCard = computed(() => {
  if (addMode.value) return true
  if (!queryText.value || !libraryData.value || libraryData.value.mode !== 'search') return false

  const resultsSection = libraryData.value.sections.find((section) => section.id === 'search-results')
  return (resultsSection?.items.length ?? 0) === 0
})

const showManualEntryHint = computed(() => {
  if (showManualAddCard.value || !queryText.value || !libraryData.value || libraryData.value.mode !== 'search') {
    return false
  }

  const resultsSection = libraryData.value.sections.find((section) => section.id === 'search-results')
  return (resultsSection?.items.length ?? 0) > 0
})

const manualAddLink = computed(() =>
  queryText.value
    ? `/shows?q=${encodeURIComponent(queryText.value)}${selectedUnwatched.value ? '&unwatched=1' : ''}&add=1`
    : `/shows?${selectedUnwatched.value ? 'unwatched=1&' : ''}add=1`,
)

function cardVariant(index: number) {
  const pattern = ['feature', 'compact', 'tall', 'standard', 'compact', 'tall'] as const
  return pattern[index % pattern.length]
}

async function handleLoadMore() {
  if (!nextCursor.value || loadingMore.value) return

  loadingMore.value = true
  try {
    const page = await fetchShowsLibraryNextPage(nextCursor.value, selectedUnwatched.value)
    const items = buildShowLibraryCards(page.items)
    extraLibraryItems.value.push(...items)
    nextCursor.value = page.nextCursor
  } finally {
    loadingMore.value = false
  }
}

useHead(() => ({
  title: 'Séries · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Entrada principal das séries no Media Pulse, unindo recorte editorial, busca e arquivo.',
    },
  ],
}))
</script>

<style scoped>
.shows-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.shows-section,
.strip-grid {
  display: grid;
  gap: 24px;
}

.strip-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.masonry-grid {
  column-count: 4;
  column-gap: 20px;
}

.entry-hint {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 24px;
  border-radius: 28px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 48%, white);
}

.entry-hint__copy {
  display: grid;
  gap: 8px;
}

.entry-hint__eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.entry-hint__copy h2 {
  margin: 0;
  font-size: clamp(1.65rem, 3vw, 2.2rem);
  line-height: 0.98;
  letter-spacing: -0.045em;
}

.entry-hint__action {
  padding: 10px 16px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  white-space: nowrap;
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
  .shows-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }

  .entry-hint {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 520px) {
  .masonry-grid {
    column-count: 1;
  }
}
</style>
