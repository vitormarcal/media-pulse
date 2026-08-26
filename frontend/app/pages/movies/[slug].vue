<template>
  <main class="movie-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Carregando...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar este filme.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <div ref="heroTarget">
        <MoviePageHero
          :movie-id="data.movieId"
          :editing-companies="activeAction === 'companies'"
          :editing-terms="activeAction === 'terms'"
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
        />
      </div>

      <section class="movie-actions" aria-labelledby="movie-actions-title">
        <div class="actions-copy">
          <p class="actions-eyebrow">Curadoria</p>
          <h2 id="movie-actions-title">Ações do filme</h2>
          <p>Escolha o que deseja revisar ou organizar.</p>
        </div>

        <div class="action-list">
          <button
            v-for="action in movieActions"
            :key="action.id"
            type="button"
            class="action-button"
            :class="{ active: activeAction === action.id }"
            :aria-pressed="activeAction === action.id"
            @click="toggleAction(action.id)"
          >
            <span>{{ action.label }}</span>
            <small>{{ activeAction === action.id ? 'Fechar' : action.description }}</small>
          </button>
        </div>
      </section>

      <div v-if="activeAction === 'metadata'" ref="metadataTarget">
        <MovieEnrichmentPanel
          :movie-id="data.movieId"
          :identifiers="data.identifiers"
          @applied="handleEnrichmentApplied"
        />
      </div>

      <MovieContextPanel :stats="data.stats" />

      <MediaRatingPanel
        media-type="movies"
        :entity-id="data.movieId"
        :initial-rating="data.rating"
        title="Nota do filme"
        minimal
      />

      <div ref="peopleTarget">
        <MoviePeoplePanel
          :movie-id="data.movieId"
          :people="data.people"
          :editing="activeAction === 'people'"
          @changed="handlePeopleChanged"
        />
      </div>

      <MovieCollectionPanel :collection="data.collection" @added="handleCatalogAdded" />

      <div ref="listsTarget">
        <MovieListsPanel
          :movie-id="data.movieId"
          :lists="data.lists"
          :editing="activeAction === 'lists'"
          @changed="handleListsChanged"
        />
      </div>

      <MovieAddWatchPanel :movie-id="data.movieId" @created="handleWatchCreated" />

      <MediaCommentsPanel
        :entity-id="data.movieId"
        media-type="movies"
        title="Comentários do filme"
        description=""
        :comments="data.comments"
        empty-label="Nenhum comentário."
      />

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
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import { useMoviePageData } from '~/composables/useMoviePageData'
import type { ManualMovieWatchCreateResponse, MovieEnrichmentApplyResponse } from '~/types/movies'

const route = useRoute()
const slug = computed(() => String(route.params.slug))
type MovieAction = 'terms' | 'companies' | 'lists' | 'people' | 'metadata'

const activeAction = ref<MovieAction | null>(null)
const heroTarget = ref<HTMLElement | null>(null)
const metadataTarget = ref<HTMLElement | null>(null)
const peopleTarget = ref<HTMLElement | null>(null)
const listsTarget = ref<HTMLElement | null>(null)
const movieActions: Array<{ id: MovieAction; label: string; description: string }> = [
  { id: 'terms', label: 'Editar gêneros e tags', description: 'Revisar marcações' },
  { id: 'companies', label: 'Revisar empresas', description: 'Ajustar vínculos' },
  { id: 'lists', label: 'Organizar em listas', description: 'Adicionar ou remover' },
  { id: 'people', label: 'Gerenciar pessoas', description: 'Revisar créditos' },
  { id: 'metadata', label: 'Enriquecer dados', description: 'Consultar TMDb' },
]

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

async function toggleAction(action: MovieAction) {
  activeAction.value = activeAction.value === action ? null : action
  if (activeAction.value === null) return

  await nextTick()
  const targets: Record<MovieAction, HTMLElement | null> = {
    terms: heroTarget.value,
    companies: heroTarget.value,
    lists: listsTarget.value,
    people: peopleTarget.value,
    metadata: metadataTarget.value,
  }
  targets[action]?.scrollIntoView({ behavior: 'smooth', block: 'start' })
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

.movie-actions {
  display: grid;
  grid-template-columns: minmax(12rem, 0.3fr) minmax(0, 1fr);
  gap: 24px;
  align-items: center;
  padding: 22px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
}

.actions-copy {
  display: grid;
  gap: 4px;
}

.actions-copy h2,
.actions-copy p {
  margin: 0;
}

.actions-copy h2 {
  font-size: 1.35rem;
  letter-spacing: -0.03em;
}

.actions-copy > p:last-child {
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
}

.actions-eyebrow {
  color: var(--base-color-brand-red);
  font-size: 0.7rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.action-list {
  display: grid;
  grid-template-columns: repeat(5, minmax(0, 1fr));
  gap: 10px;
}

.action-button {
  display: grid;
  gap: 4px;
  min-height: 68px;
  padding: 12px 14px;
  border: 2px solid transparent;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  text-align: left;
  cursor: pointer;
}

.action-button:hover {
  background: color-mix(in srgb, var(--base-color-surface-warm) 80%, white);
}

.action-button:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 2px;
}

.action-button.active {
  border-color: var(--base-color-brand-red);
  background: white;
}

.action-button span {
  font-size: 0.82rem;
  font-weight: 700;
}

.action-button small {
  color: var(--base-color-text-secondary);
  font-size: 0.7rem;
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

@media (max-width: 1100px) {
  .action-list {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .movie-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }

  .movie-actions {
    grid-template-columns: 1fr;
    padding: 18px;
  }

  .action-list {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 420px) {
  .action-list {
    grid-template-columns: 1fr;
  }
}
</style>
