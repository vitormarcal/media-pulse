<template>
  <main class="movie-page">
    <div v-if="status === 'pending' && !data" class="state-card">
      <p>Carregando...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar este filme.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <div v-if="data.enrichment.status === 'PENDING'" class="enrichment-state" role="status">
        <span class="enrichment-dot" aria-hidden="true" />
        <span>{{
          data.enrichment.tmdbResolution.status === 'PENDING'
            ? 'Localizando este filme no TMDb…'
            : 'Completando informações…'
        }}</span>
      </div>

      <div v-else-if="data.enrichment.status === 'RETRY_SCHEDULED'" class="enrichment-state blocked" role="status">
        <span>Algumas informações não puderam ser atualizadas. Tentaremos novamente mais tarde.</span>
      </div>

      <div v-else-if="data.enrichment.status === 'BLOCKED' && activeAction" class="enrichment-state blocked">
        <span>Vincule este filme ao TMDb para completar pessoas, empresas e marcações.</span>
      </div>

      <div>
        <MoviePageHero
          ref="movieHero"
          :title="data.title"
          :subtitle="heroSubtitle"
          :description="data.description"
          :gallery="data.gallery.slice(0, 4)"
          :image-count="data.gallery.length"
          @view-image="galleryViewer?.open($event)"
        >
          <template v-if="owner.authenticated" #image-actions>
            <MediaImageSelector
              :key="data.movieId"
              media-type="movies"
              :entity-id="data.movieId"
              :title="data.title"
              compact
              @imported="refresh()"
            />
          </template>
          <template
            v-if="owner.authenticated || data.comments.length || data.rating != null || data.favorite || data.abandoned"
            #personal
          >
            <div class="personal-memory">
              <div v-if="owner.authenticated || data.favorite || data.abandoned" class="personal-context">
                <MediaPersonalMarks
                  :key="data.movieId"
                  v-model:favorite="data.favorite"
                  v-model:abandoned="data.abandoned"
                  media-type="movies"
                  :entity-id="data.movieId"
                  @saved="refresh()"
                />
              </div>

              <MediaCommentsPanel
                v-if="owner.authenticated || data.comments.length || data.rating != null"
                :entity-id="data.movieId"
                media-type="movies"
                title="Comentários"
                description=""
                :comments="data.comments"
                empty-label="Nenhum comentário."
                compact
                editorial
              >
                <template #meta>
                  <MediaRatingPanel
                    v-if="owner.authenticated || data.rating != null"
                    :key="`rating-${data.movieId}`"
                    media-type="movies"
                    :entity-id="data.movieId"
                    :initial-rating="data.rating"
                    label="Nota"
                    compact
                    minimal
                  />
                </template>
              </MediaCommentsPanel>
            </div>
          </template>
        </MoviePageHero>
      </div>

      <ManagedMediaImageGallery
        :key="data.movieId"
        ref="galleryViewer"
        v-model:images="data.gallery"
        v-model:cover-url="data.coverUrl"
        media-type="movies"
        :entity-id="data.movieId"
        :title="data.title"
        :refresh="refresh"
        @closed="restoreGalleryFocus"
      />

      <section class="sessions-section detail-surface" aria-labelledby="sessions-title">
        <div class="section-header">
          <h2 id="sessions-title">Sessões</h2>
          <button
            v-if="owner.authenticated"
            class="edit-button"
            type="button"
            :aria-expanded="watchFormOpen"
            @click="watchFormOpen = !watchFormOpen"
          >
            {{ watchFormOpen ? 'Fechar registro' : 'Registrar sessão' }}
          </button>
        </div>
        <MovieAddWatchPanel
          v-if="owner.authenticated && watchFormOpen"
          :movie-id="data.movieId"
          @created="handleWatchCreated"
        />
        <MovieWatchTimeline :movie-id="data.movieId" :watches="data.recentWatches" @deleted="handleWatchDeleted" />
      </section>

      <section class="movie-details detail-surface" aria-labelledby="details-title">
        <div class="section-header">
          <h2 id="details-title">Sobre o filme</h2>
          <button
            v-if="owner.authenticated"
            class="edit-button"
            type="button"
            :aria-expanded="activeAction === 'metadata'"
            @click="toggleAction('metadata')"
          >
            {{ activeAction === 'metadata' ? 'Fechar atualização' : 'Atualizar dados' }}
          </button>
        </div>
        <MovieEnrichmentPanel
          v-if="owner.authenticated && activeAction === 'metadata'"
          :movie-id="data.movieId"
          :identifiers="data.identifiers"
          @applied="handleEnrichmentApplied"
        />
        <div v-if="data.people.visibleCount || owner.authenticated" class="detail-group">
          <MoviePeoplePanel
            :movie-id="data.movieId"
            :people="data.people"
            :enrichment-status="data.enrichment.credits"
            :editing="activeAction === 'people'"
            @changed="handlePeopleChanged"
          >
            <template #actions>
              <button
                v-if="owner.authenticated"
                class="edit-button"
                type="button"
                :aria-expanded="activeAction === 'people'"
                @click="toggleAction('people')"
              >
                {{ activeAction === 'people' ? 'Concluir edição' : 'Editar créditos' }}
              </button>
            </template>
          </MoviePeoplePanel>
        </div>
        <div class="detail-group">
          <MovieTermsPanel
            :movie-id="data.movieId"
            :terms="data.terms"
            :enrichment-status="data.enrichment.terms"
            :editing="activeAction === 'terms'"
            embedded
            @changed="handleTermsChanged"
          >
            <template #actions>
              <button
                v-if="owner.authenticated"
                class="edit-button"
                type="button"
                :aria-expanded="activeAction === 'terms'"
                @click="toggleAction('terms')"
              >
                {{ activeAction === 'terms' ? 'Concluir edição' : 'Editar gêneros e tags' }}
              </button>
            </template>
          </MovieTermsPanel>
        </div>
        <div class="detail-group">
          <MovieCompaniesPanel
            :movie-id="data.movieId"
            :companies="data.companies"
            :enrichment-status="data.enrichment.companies"
            :editing="activeAction === 'companies'"
            @changed="handleCompaniesChanged"
          >
            <template #actions>
              <button
                v-if="owner.authenticated"
                class="edit-button"
                type="button"
                :aria-expanded="activeAction === 'companies'"
                @click="toggleAction('companies')"
              >
                {{ activeAction === 'companies' ? 'Concluir edição' : 'Editar empresas' }}
              </button>
            </template>
          </MovieCompaniesPanel>
        </div>
      </section>

      <MovieCollectionPanel :collection="data.collection" @added="handleCatalogAdded" />
      <MovieCollectionTagsPanel v-if="owner.authenticated" :key="data.movieId" :movie-id="data.movieId" />
      <section v-if="owner.authenticated || data.lists.items.length" class="movie-details" aria-label="Listas">
        <button
          v-if="owner.authenticated"
          class="edit-button"
          type="button"
          :aria-expanded="activeAction === 'lists'"
          @click="toggleAction('lists')"
        >
          {{ activeAction === 'lists' ? 'Concluir edição' : 'Organizar em listas' }}
        </button>
        <MovieListsPanel
          :movie-id="data.movieId"
          :lists="data.lists"
          :editing="activeAction === 'lists'"
          @changed="handleListsChanged"
        />
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import ManagedMediaImageGallery from '~/components/media/ManagedMediaImageGallery.vue'
import MediaImageSelector from '~/components/media/MediaImageSelector.vue'
import MovieCollectionTagsPanel from '~/components/movies/MovieCollectionTagsPanel.vue'
import MediaPersonalMarks from '~/components/media/MediaPersonalMarks.vue'
import MovieAddWatchPanel from '~/components/movies/MovieAddWatchPanel.vue'
import MovieCollectionPanel from '~/components/movies/MovieCollectionPanel.vue'
import MovieCompaniesPanel from '~/components/movies/MovieCompaniesPanel.vue'
import MovieTermsPanel from '~/components/movies/MovieTermsPanel.vue'
import MovieEnrichmentPanel from '~/components/movies/MovieEnrichmentPanel.vue'
import MovieListsPanel from '~/components/movies/MovieListsPanel.vue'
import MoviePageHero from '~/components/movies/MoviePageHero.vue'
import MoviePeoplePanel from '~/components/movies/MoviePeoplePanel.vue'
import MovieWatchTimeline from '~/components/movies/MovieWatchTimeline.vue'
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import { useMoviePageData } from '~/composables/useMoviePageData'
import type { ManualMovieWatchCreateResponse, MovieEnrichmentApplyResponse } from '~/types/movies'

const movieHero = ref<InstanceType<typeof MoviePageHero> | null>(null)
const galleryViewer = ref<InstanceType<typeof ManagedMediaImageGallery> | null>(null)
const owner = useOwnerAuth()

const route = useRoute()
const slug = computed(() => String(route.params.slug))
type MovieAction = 'terms' | 'companies' | 'lists' | 'people' | 'metadata'

const activeAction = ref<MovieAction | null>(null)
const watchFormOpen = ref(false)
const { data, error, status, refresh } = await useMoviePageData(slug.value)
let enrichmentPoll: ReturnType<typeof setInterval> | null = null
let enrichmentPollCount = 0

onMounted(() => {
  enrichmentPoll = setInterval(async () => {
    if (data.value?.enrichment.status !== 'PENDING' || enrichmentPollCount >= 15) {
      if (enrichmentPoll) clearInterval(enrichmentPoll)
      enrichmentPoll = null
      return
    }
    enrichmentPollCount++
    await refresh()
  }, 4000)
})

onBeforeUnmount(() => {
  if (enrichmentPoll) clearInterval(enrichmentPoll)
})

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

function restoreGalleryFocus(needsFocusFallback: boolean) {
  if (needsFocusFallback) movieHero.value?.focusGallery()
}

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

function toggleAction(action: MovieAction) {
  activeAction.value = activeAction.value === action ? null : action
}
</script>

<style scoped>
.movie-page {
  display: grid;
  gap: 32px;
  width: min(1120px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.enrichment-state {
  display: flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  margin: 0;
  padding: 8px 12px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
}

.enrichment-state.blocked {
  color: var(--base-color-text-primary);
}

.enrichment-dot {
  width: 7px;
  height: 7px;
  border-radius: 50%;
  background: var(--base-color-brand-red);
  animation: enrichment-pulse 1.4s ease-in-out infinite;
}

@keyframes enrichment-pulse {
  50% {
    opacity: 0.35;
  }
}

.personal-memory,
.personal-context,
.sessions-section,
.movie-details {
  display: grid;
  gap: 20px;
}
.personal-memory {
  gap: 16px;
}
.detail-surface {
  padding: clamp(20px, 3vw, 32px);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 35%, white);
}
.detail-group {
  display: grid;
  gap: 16px;
  padding-top: 20px;
  border-top: 1px solid color-mix(in srgb, var(--base-color-border) 35%, white);
}
.personal-context {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px 32px;
}
.personal-context :deep(.compact-row) {
  justify-content: flex-start;
}
.section-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 16px;
  flex-wrap: wrap;
}
.section-header h2 {
  margin: 0;
  font-size: 1.25rem;
}
.edit-button {
  justify-self: start;
  min-height: 44px;
  padding: 10px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.82rem;
  cursor: pointer;
}
.edit-button:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
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
    width: calc(100vw - 20px);
    padding: 20px 0 64px;
  }
}
</style>
