<template>
  <main class="movie-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página do filme...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar este filme.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <MoviePageHero
        :movie-id="data.movieId"
        :editing="editMode"
        :title="data.title"
        :subtitle="heroSubtitle"
        :description="data.description"
        :gallery="data.gallery"
        :hero-meta="data.heroMeta"
        :identifiers="data.identifiers"
        :companies="data.companies"
        :terms="data.terms"
        @companies-changed="handleCompaniesChanged"
        @terms-changed="handleTermsChanged"
        @toggle-editing="toggleEditing"
      />

      <MovieEnrichmentPanel
        v-if="editMode"
        :movie-id="data.movieId"
        :identifiers="data.identifiers"
        @applied="handleEnrichmentApplied"
      />

      <MovieContextPanel :stats="data.stats" />

      <MoviePeoplePanel
        :movie-id="data.movieId"
        :people="data.people"
        :editing="editMode"
        @changed="handlePeopleChanged"
      />

      <MovieCollectionPanel :collection="data.collection" @added="handleCatalogAdded" />

      <MovieListsPanel :movie-id="data.movieId" :lists="data.lists" :editing="editMode" @changed="handleListsChanged" />

      <MovieAddWatchPanel :movie-id="data.movieId" @created="handleWatchCreated" />

      <MovieWatchTimeline :movie-id="data.movieId" :watches="data.recentWatches" @deleted="handleWatchDeleted" />
    </template>
  </main>
</template>

<script setup lang="ts">
import MovieAddWatchPanel from '~/components/movies/MovieAddWatchPanel.vue'
import MovieCollectionPanel from '~/components/movies/MovieCollectionPanel.vue'
import MovieContextPanel from '~/components/movies/MovieContextPanel.vue'
import MovieEnrichmentPanel from '~/components/movies/MovieEnrichmentPanel.vue'
import MovieListsPanel from '~/components/movies/MovieListsPanel.vue'
import MoviePageHero from '~/components/movies/MoviePageHero.vue'
import MoviePeoplePanel from '~/components/movies/MoviePeoplePanel.vue'
import MovieWatchTimeline from '~/components/movies/MovieWatchTimeline.vue'
import { useMoviePageData } from '~/composables/useMoviePageData'
import type { ManualMovieWatchCreateResponse, MovieEnrichmentApplyResponse } from '~/types/movies'

const route = useRoute()
const slug = computed(() => String(route.params.slug))
const editMode = ref(false)

const { data, error, status, refresh } = await useMoviePageData(slug.value)

const heroSubtitle = computed(() => {
  if (!data.value) return null

  if (data.value.originalTitle !== data.value.title && data.value.year) {
    return `${data.value.originalTitle} · ${data.value.year}`
  }

  if (data.value.originalTitle !== data.value.title) {
    return data.value.originalTitle
  }

  return data.value.year ? String(data.value.year) : null
})

useHead(() => ({
  title: data.value ? `${data.value.title} · Media Pulse` : 'Filme · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value?.description || 'Página interna de filme no Media Pulse.',
    },
  ],
}))

async function handleEnrichmentApplied(_response: MovieEnrichmentApplyResponse) {
  await refresh()
}

async function handleWatchCreated(_response: ManualMovieWatchCreateResponse) {
  await refresh()
}

async function handleWatchDeleted() {
  await refresh()
}

async function handleCatalogAdded() {
  await refresh()
}

async function handleTermsChanged() {
  await refresh()
}

async function handleListsChanged() {
  await refresh()
}

async function handleCompaniesChanged() {
  await refresh()
}

async function handlePeopleChanged() {
  await refresh()
}

function toggleEditing() {
  editMode.value = !editMode.value
}
</script>

<style scoped>
.movie-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
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
  .movie-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
