<template>
  <main class="music-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página de música...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível montar a página de música com os dados atuais.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <template v-if="showEditorialLayer && collectionData">
        <MusicCollectionHero
          :title="collectionData.hero.title"
          :intro="collectionData.hero.intro"
          :lead="collectionData.hero.lead"
          :supporting="collectionData.hero.supporting"
        />

        <section class="music-section">
          <SectionHeading
            eyebrow="Discos em rotação"
            title="Os álbuns que estão sustentando o momento"
            description=""
            summary=""
          />

          <div class="masonry-grid">
            <MediaPosterCard
              v-for="(item, index) in collectionData.featuredAlbums"
              :key="item.id"
              :item="item"
              :variant="cardVariant(index)"
            />
          </div>
        </section>

        <MusicCollectionContext
          :eyebrow="collectionData.context.eyebrow"
          :title="collectionData.context.title"
          :description="collectionData.context.description"
          :summary="collectionData.context.summary"
          :metrics="collectionData.context.metrics"
        />

        <section class="music-section">
          <SectionHeading
            eyebrow="Artistas em primeiro plano"
            title="Quem mais puxou a escuta recente"
            description=""
            summary=""
          />

          <div class="strip-grid">
            <MusicStripCard
              v-for="item in collectionData.topArtists"
              :key="item.id"
              kicker="Artista"
              :title="item.title"
              :subtitle="item.subtitle"
              :meta="item.meta"
              :detail="item.detail"
              :image-url="item.imageUrl"
              :href="item.href"
              variant="large"
            />
          </div>
        </section>

        <section class="music-section">
          <SectionHeading
            eyebrow="Faixas que insistiram em voltar"
            title="A camada fina do replay recente"
            description=""
            summary=""
          />

          <div class="strip-grid">
            <MusicStripCard
              v-for="item in collectionData.topTracks"
              :key="item.id"
              kicker="Faixa"
              :title="item.title"
              :subtitle="item.subtitle"
              :meta="item.meta"
              :detail="item.detail"
              :image-url="item.imageUrl"
              :href="item.href"
            />
          </div>
        </section>

        <section class="music-section">
          <SectionHeading
            eyebrow="Fronteira de descoberta"
            title="O que ainda está esperando a primeira audição"
            description=""
            summary=""
          />

          <div class="strip-grid">
            <MusicStripCard
              v-for="item in collectionData.discoveryAlbums"
              :key="item.id"
              kicker="Descoberta"
              :title="item.title"
              :subtitle="item.subtitle"
              :meta="item.meta"
              :detail="item.detail"
              :image-url="item.imageUrl"
              :href="item.href"
            />
          </div>
        </section>
      </template>

      <template v-else-if="libraryData">
        <MusicLibraryHero
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

        <MusicLibraryFilters
          :query="libraryData.filters.query"
          :selected-kind="libraryData.filters.selectedKind"
          :selected-year="libraryData.filters.selectedYear"
          :tabs="libraryData.filters.tabs"
          :years="libraryData.filters.years"
        />

        <MusicCollectionContext
          :eyebrow="libraryData.context.eyebrow"
          :title="libraryData.context.title"
          :description="libraryData.context.description"
          :summary="libraryData.context.summary"
          :metrics="libraryData.context.metrics"
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
    </template>
  </main>
</template>

<script setup lang="ts">
import MediaPosterCard from '~/components/home/MediaPosterCard.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'
import MusicCollectionContext from '~/components/music/MusicCollectionContext.vue'
import MusicCollectionHero from '~/components/music/MusicCollectionHero.vue'
import MusicLibraryFilters from '~/components/music/MusicLibraryFilters.vue'
import MusicLibraryGrid from '~/components/music/MusicLibraryGrid.vue'
import MusicLibraryHero from '~/components/music/MusicLibraryHero.vue'
import MusicStripCard from '~/components/music/MusicStripCard.vue'
import { fetchMusicCollectionData } from '~/composables/useMusicCollectionData'
import { fetchMusicLibraryNextPage, fetchMusicLibraryPageData } from '~/composables/useMusicLibraryData'
import type {
  AlbumLibraryPageResponse,
  ArtistLibraryPageResponse,
  MusicCollectionData,
  MusicLibraryCardModel,
  MusicLibraryKind,
  MusicLibraryPageData,
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

const archiveView = computed(() => route.query.view === 'archive')

const showEditorialLayer = computed(() => !queryText.value && selectedYear.value == null && !archiveView.value)

const { data, error, status } = await useAsyncData(
  'music-page',
  async () => {
    if (showEditorialLayer.value) {
      return {
        mode: 'collection' as const,
        collection: await fetchMusicCollectionData(),
        library: null,
      }
    }

    return {
      mode: 'library' as const,
      collection: null,
      library: await fetchMusicLibraryPageData({
        q: queryText.value,
        kind: selectedKind.value,
        year: selectedYear.value,
      }),
    }
  },
  {
    watch: [queryText, selectedKind, selectedYear, archiveView],
  },
)

const collectionData = computed<MusicCollectionData | null>(() => data.value?.collection ?? null)
const libraryData = computed<MusicLibraryPageData | null>(() => data.value?.library ?? null)

const nextCursor = ref<string | null>(null)
const extraItems = ref<MusicLibraryCardModel[]>([])
const loadingMore = ref(false)

watch(
  libraryData,
  (value) => {
    nextCursor.value = value?.libraryCursor ?? null
    extraItems.value = []
  },
  { immediate: true },
)

const displaySections = computed(() => {
  if (!libraryData.value) return []

  return libraryData.value.sections.map((section, index) =>
    index === 0 && libraryData.value?.mode === 'library'
      ? { ...section, items: [...section.items, ...extraItems.value] }
      : section,
  )
})

const canLoadMore = computed(() => libraryData.value?.mode === 'library' && !!nextCursor.value)

function cardVariant(index: number) {
  const pattern = ['feature', 'compact', 'tall', 'standard', 'compact', 'tall'] as const
  return pattern[index % pattern.length]
}

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
  title:
    showEditorialLayer.value || !libraryData.value
      ? 'Música · Media Pulse'
      : libraryData.value.mode === 'search'
        ? 'Busca de Música · Media Pulse'
        : libraryData.value.mode === 'year'
          ? `${libraryData.value.filters.selectedYear} · Música · Media Pulse`
          : 'Arquivo de Música · Media Pulse',
  meta: [
    {
      name: 'description',
      content:
        showEditorialLayer.value || !libraryData.value
          ? 'Recorte editorial da escuta recente no Media Pulse, com foco em álbuns, artistas e faixas em rotação.'
          : 'Arquivo completo de música no Media Pulse, com exploração por artistas, álbuns e faixas.',
    },
  ],
}))
</script>

<style scoped>
.music-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.music-section,
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
  .music-page {
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
