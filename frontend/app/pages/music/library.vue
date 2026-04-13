<template>
  <main class="music-library-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a biblioteca de música...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível montar a biblioteca de música com os dados atuais.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <MusicLibraryHero
        :title="data.hero.title"
        :intro="data.hero.intro"
        :back-link="data.hero.backLink"
        :back-label="data.hero.backLabel"
        :accent-link="data.hero.accentLink"
        :accent-label="data.hero.accentLabel"
        :spotlight="data.hero.spotlight"
      />

      <MusicLibraryFilters
        :query="data.filters.query"
        :selected-kind="data.filters.selectedKind"
        :selected-year="data.filters.selectedYear"
        :tabs="data.filters.tabs"
        :years="data.filters.years"
      />

      <MusicCollectionContext
        :eyebrow="data.context.eyebrow"
        :title="data.context.title"
        :description="data.context.description"
        :summary="data.context.summary"
        :metrics="data.context.metrics"
      />

      <MusicLibraryGrid
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
          {{ loadingMore ? 'Buscando mais entradas...' : 'Carregar mais da biblioteca' }}
        </button>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import MusicCollectionContext from '~/components/music/MusicCollectionContext.vue'
import MusicLibraryFilters from '~/components/music/MusicLibraryFilters.vue'
import MusicLibraryGrid from '~/components/music/MusicLibraryGrid.vue'
import MusicLibraryHero from '~/components/music/MusicLibraryHero.vue'
import { fetchMusicLibraryNextPage, fetchMusicLibraryPageData } from '~/composables/useMusicLibraryData'
import type {
  AlbumLibraryPageResponse,
  ArtistLibraryPageResponse,
  MusicLibraryCardModel,
  MusicLibraryKind,
  TrackLibraryPageResponse,
} from '~/types/music'
import { buildMusicLibraryCards } from '~/utils/music'

const route = useRoute()

const queryText = computed(() => {
  const value = route.query.q
  return typeof value === 'string' ? value.trim() : ''
})

const selectedKind = computed<MusicLibraryKind>(() => {
  const value = route.query.kind
  return value === 'artists' || value === 'tracks' ? value : 'albums'
})

const selectedYear = computed<number | null>(() => {
  const value = route.query.year
  if (typeof value !== 'string' || !value.trim()) return null

  const parsed = Number(value)
  return Number.isFinite(parsed) ? parsed : null
})

const { data, error, status } = await useAsyncData(
  'music-library-page',
  () => fetchMusicLibraryPageData({ q: queryText.value, kind: selectedKind.value, year: selectedYear.value }),
  {
    watch: [queryText, selectedKind, selectedYear],
  },
)

const nextCursor = ref<string | null>(null)
const extraItems = ref<MusicLibraryCardModel[]>([])
const loadingMore = ref(false)

watch(data, (value) => {
  nextCursor.value = value?.libraryCursor ?? null
  extraItems.value = []
}, { immediate: true })

const displaySections = computed(() => {
  if (!data.value) return []

  return data.value.sections.map((section, index) => (
    index === 0 && data.value?.mode === 'library'
      ? { ...section, items: [...section.items, ...extraItems.value] }
      : section
  ))
})

const canLoadMore = computed(() => data.value?.mode === 'library' && !!nextCursor.value)

async function handleLoadMore() {
  if (!nextCursor.value || loadingMore.value) return

  loadingMore.value = true

  try {
    const page = await fetchMusicLibraryNextPage(selectedKind.value, nextCursor.value)

    if (selectedKind.value === 'artists') {
      extraItems.value.push(...buildMusicLibraryCards('artists', (page as ArtistLibraryPageResponse).items))
    } else if (selectedKind.value === 'albums') {
      extraItems.value.push(...buildMusicLibraryCards('albums', (page as AlbumLibraryPageResponse).items))
    } else {
      extraItems.value.push(...buildMusicLibraryCards('tracks', (page as TrackLibraryPageResponse).items))
    }

    nextCursor.value = page.nextCursor
  } finally {
    loadingMore.value = false
  }
}

useHead(() => ({
  title: 'Biblioteca de Música · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Arquivo completo de música no Media Pulse, com exploração por artistas, álbuns e faixas.',
    },
  ],
}))
</script>

<style scoped>
.music-library-page {
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
  .music-library-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
