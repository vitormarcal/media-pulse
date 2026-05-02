<template>
  <main class="movies-page">
    <div v-if="editorialStatus === 'pending' || libraryStatus === 'pending'" class="state-card">
      <p>Montando a página de filmes...</p>
    </div>

    <div v-else-if="editorialError || libraryError" class="state-card error">
      <p>Não foi possível montar a página de filmes com os dados atuais.</p>
      <pre>{{ editorialError?.message || libraryError?.message }}</pre>
    </div>

    <template v-else-if="editorialData && libraryData">
      <template v-if="showEditorialLayer">
        <MoviesCollectionHero
          :title="editorialData.hero.title"
          :intro="editorialData.hero.intro"
          :lead="editorialData.hero.lead"
          :supporting="editorialData.hero.supporting"
          accent-link="/movies?add=1"
          accent-label="Adicionar filme"
        />

        <section class="movies-section">
          <SectionHeading
            eyebrow="Em circulação"
            title="Os filmes que ainda seguem reverberando"
            description=""
            summary=""
          />

          <div class="strip-grid">
            <MediaStripCard
              v-for="item in editorialData.featuredSessions"
              :key="item.id"
              :item="item"
              variant="large"
            />
          </div>
        </section>

        <MoviesCollectionContext
          :eyebrow="editorialData.context.eyebrow"
          :title="editorialData.context.title"
          :description="editorialData.context.description"
          :summary="editorialData.context.summary"
          :metrics="editorialData.context.metrics"
        />

        <section id="movies-recent" class="movies-section">
          <SectionHeading
            eyebrow="Sessões recentes"
            title="O que acabou de voltar para a tela"
            description=""
            summary=""
          />

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

      <MoviesLibraryHero
        v-else
        :title="libraryData.hero.title"
        :intro="libraryData.hero.intro"
        :back-link="libraryData.hero.backLink"
        :back-label="libraryData.hero.backLabel"
        :accent-link="libraryData.hero.accentLink"
        :accent-label="libraryData.hero.accentLabel"
        :utility-link="libraryData.hero.utilityLink"
        :utility-label="libraryData.hero.utilityLabel"
        :spotlight="libraryData.hero.spotlight"
      />

      <MoviesLibraryFilters
        :query="libraryData.filters.query"
        :selected-year="libraryData.filters.selectedYear"
        :selected-unwatched="libraryData.filters.selectedUnwatched"
        :years="libraryData.filters.years"
      />

      <section v-if="libraryData.mode === 'library'" class="entry-rail">
        <NuxtLink
          v-for="entry in entryCards"
          :key="entry.id"
          class="entry-card"
          :to="entry.href"
          :style="
            entry.imageUrl
              ? {
                  backgroundImage: `linear-gradient(180deg, rgba(255,255,255,0.84), rgba(246,243,238,0.96)), url('${entry.imageUrl}')`,
                }
              : undefined
          "
        >
          <p class="entry-card__eyebrow">{{ entry.eyebrow }}</p>
          <h2>{{ entry.title }}</h2>
          <p class="entry-card__meta">{{ entry.meta }}</p>
        </NuxtLink>
      </section>

      <MovieManualAddCard v-if="showManualAddCard" :initial-title="queryText" />

      <section v-else-if="showManualEntryHint" class="entry-hint">
        <div class="entry-hint__copy">
          <p class="entry-hint__eyebrow">Entrada manual</p>
          <h2>Não era o filme certo?</h2>
        </div>

        <NuxtLink class="entry-hint__action" :to="manualAddLink">Adicionar filme</NuxtLink>
      </section>

      <MoviesCollectionContext
        :eyebrow="libraryData.context.eyebrow"
        :title="libraryData.context.title"
        :description="libraryData.context.description"
        :summary="libraryData.context.summary"
        :metrics="libraryData.context.metrics"
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
        <button type="button" class="load-more" :disabled="loadingMore" @click="handleLoadMore">
          {{ loadingMore ? 'Buscando mais filmes...' : 'Carregar mais filmes' }}
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
import MoviesCollectionContext from '~/components/movies/MoviesCollectionContext.vue'
import MoviesCollectionHero from '~/components/movies/MoviesCollectionHero.vue'
import MoviesLibraryFilters from '~/components/movies/MoviesLibraryFilters.vue'
import MoviesLibraryGrid from '~/components/movies/MoviesLibraryGrid.vue'
import MoviesLibraryHero from '~/components/movies/MoviesLibraryHero.vue'
import MovieManualAddCard from '~/components/movies/MovieManualAddCard.vue'
import { useMoviesCollectionData } from '~/composables/useMoviesCollectionData'
import { fetchMoviesLibraryNextPage, fetchMoviesLibraryPageData } from '~/composables/useMoviesLibraryData'
import type { MovieLibraryCardModel } from '~/types/movies'
import { buildMovieLibraryCards } from '~/utils/movies'

const route = useRoute()
const { resolveMediaUrl } = useMediaUrl()

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

const { data: editorialData, error: editorialError, status: editorialStatus } = await useMoviesCollectionData()
const {
  data: libraryData,
  error: libraryError,
  status: libraryStatus,
} = await useAsyncData(
  'movies-main-library-page',
  () =>
    fetchMoviesLibraryPageData({ q: queryText.value, year: selectedYear.value, unwatched: selectedUnwatched.value }),
  {
    watch: [queryText, selectedYear, selectedUnwatched],
  },
)

const nextCursor = ref<string | null>(null)
const extraLibraryItems = ref<MovieLibraryCardModel[]>([])
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

const entryCards = computed(() => {
  const recentImage = resolveMediaUrl(editorialData.value?.recentMoments[0]?.imageUrl ?? null)
  const collectionImage = resolveMediaUrl(
    editorialData.value?.hero.lead?.imageUrl ?? editorialData.value?.hero.supporting[0]?.imageUrl ?? null,
  )
  const addImage = resolveMediaUrl(
    libraryData.value?.hero.spotlight?.imageUrl ?? editorialData.value?.featuredSessions[0]?.imageUrl ?? null,
  )

  return [
    {
      id: 'lists',
      href: '/movies/lists',
      eyebrow: 'Listas',
      title: 'Entrar por recortes manuais',
      meta: 'Afinidade, ocasião e obsessão antes do arquivo inteiro.',
      imageUrl: recentImage,
    },
    {
      id: 'collections',
      href: '/movies/collections',
      eyebrow: 'Coleções',
      title: 'Abrir franquias e conjuntos',
      meta: 'Sagas já consolidadas como portas de entrada próprias.',
      imageUrl: collectionImage,
    },
    {
      id: 'add',
      href: selectedUnwatched.value ? '/movies?unwatched=1&add=1' : '/movies?add=1',
      eyebrow: 'Adicionar',
      title: 'Trazer um filme novo',
      meta: 'Entrada manual sem sair da página principal.',
      imageUrl: addImage,
    },
  ]
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
    ? `/movies?q=${encodeURIComponent(queryText.value)}${selectedUnwatched.value ? '&unwatched=1' : ''}&add=1`
    : `/movies?${selectedUnwatched.value ? 'unwatched=1&' : ''}add=1`,
)

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
  const page = await fetchMoviesLibraryNextPage(cursor, selectedUnwatched.value)
  const items = buildMovieLibraryCards(page.items)
  extraLibraryItems.value.push(...items)
  nextCursor.value = page.nextCursor
}

useHead(() => ({
  title: 'Filmes · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Entrada principal dos filmes no Media Pulse, unindo recorte editorial, busca, biblioteca e curadoria.',
    },
  ],
}))
</script>

<style scoped>
.movies-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.movies-section,
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

.entry-rail {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 18px;
}

.entry-card {
  display: grid;
  gap: 10px;
  min-height: 16rem;
  align-content: end;
  padding: 24px;
  border-radius: 28px;
  background-image:
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.98)),
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 28%);
  background-size: cover;
  background-position: center;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 48%, white);
}

.entry-card__eyebrow,
.entry-hint__eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.entry-card h2,
.entry-card__meta {
  margin: 0;
}

.entry-card h2 {
  font-size: clamp(1.7rem, 3vw, 2.4rem);
  line-height: 0.96;
  letter-spacing: -0.05em;
  color: var(--base-color-text-primary);
}

.entry-card__meta {
  max-width: 22rem;
  color: var(--base-color-text-secondary);
  line-height: 1.5;
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

.entry-hint__copy h2 {
  margin: 0;
}

.entry-hint__copy h2 {
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
  .strip-grid,
  .entry-rail {
    grid-template-columns: 1fr;
  }

  .masonry-grid {
    column-count: 2;
  }
}

@media (max-width: 720px) {
  .movies-page {
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
