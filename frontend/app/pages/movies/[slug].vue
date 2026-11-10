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
        />
      </div>

      <MediaImageGallery
        ref="galleryViewer"
        :images="data.gallery"
        :title="data.title"
        viewer-only
        :busy="imageBusy"
        @image-change="handleGalleryImageChanged"
        @closed="handleGalleryClosed"
      >
        <template v-if="owner.authenticated" #actions="{ image, index }">
          <div class="image-actions" :aria-label="`Ações da imagem ${index + 1}`">
            <span v-if="image === data.coverUrl" class="cover-label">Capa atual</span>
            <button v-else type="button" :disabled="imageBusy" @click="manageImage(image, false)">
              Definir como capa
            </button>
            <button type="button" :disabled="imageBusy" @click="confirmRemoval = image">Remover imagem</button>
            <div v-if="confirmRemoval === image" class="remove-confirmation">
              <p>
                Remover esta imagem do filme?{{
                  image === data.coverUrl ? ' Outra imagem será usada como capa, se disponível.' : ''
                }}
              </p>
              <button type="button" :disabled="imageBusy" @click="manageImage(image, true)">Confirmar remoção</button>
              <button type="button" :disabled="imageBusy" @click="confirmRemoval = ''">Cancelar</button>
            </div>
            <p v-if="imageBusy" class="image-feedback" role="status">Salvando alteração…</p>
            <p v-if="imageError" class="image-feedback image-error" role="alert">{{ imageError }}</p>
            <p v-if="imageMessage" class="image-feedback" role="status">{{ imageMessage }}</p>
          </div>
        </template>
      </MediaImageGallery>
      <p v-if="imageMessage && !data.gallery.length" role="status">{{ imageMessage }}</p>

      <div
        v-if="owner.authenticated || data.favorite || data.abandoned || data.rating != null"
        class="personal-context"
      >
        <MediaPersonalMarks
          v-if="owner.authenticated || data.favorite || data.abandoned"
          :key="data.movieId"
          v-model:favorite="data.favorite"
          v-model:abandoned="data.abandoned"
          media-type="movies"
          :entity-id="data.movieId"
          @saved="refresh()"
        />
        <MediaRatingPanel
          :key="`rating-${data.movieId}`"
          media-type="movies"
          :entity-id="data.movieId"
          :initial-rating="data.rating"
          compact
          minimal
        />
      </div>

      <MediaCommentsPanel
        :entity-id="data.movieId"
        media-type="movies"
        title="Comentários"
        description=""
        :comments="data.comments"
        empty-label="Nenhum comentário."
        compact
      />

      <section class="sessions-section" aria-labelledby="sessions-title">
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

      <section class="movie-details" aria-labelledby="details-title">
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
        <div>
          <button
            v-if="owner.authenticated"
            class="edit-button"
            type="button"
            :aria-expanded="activeAction === 'people'"
            @click="toggleAction('people')"
          >
            {{ activeAction === 'people' ? 'Concluir edição' : 'Editar créditos' }}
          </button>
          <MoviePeoplePanel
            :movie-id="data.movieId"
            :people="data.people"
            :enrichment-status="data.enrichment.credits"
            :editing="activeAction === 'people'"
            @changed="handlePeopleChanged"
          />
        </div>
        <div>
          <button
            v-if="owner.authenticated"
            class="edit-button"
            type="button"
            :aria-expanded="activeAction === 'terms'"
            @click="toggleAction('terms')"
          >
            {{ activeAction === 'terms' ? 'Concluir edição' : 'Editar gêneros e tags' }}
          </button>
          <MovieTermsPanel
            :movie-id="data.movieId"
            :terms="data.terms"
            :enrichment-status="data.enrichment.terms"
            :editing="activeAction === 'terms'"
            embedded
            @changed="handleTermsChanged"
          />
        </div>
        <div>
          <button
            v-if="owner.authenticated"
            class="edit-button"
            type="button"
            :aria-expanded="activeAction === 'companies'"
            @click="toggleAction('companies')"
          >
            {{ activeAction === 'companies' ? 'Concluir edição' : 'Editar empresas' }}
          </button>
          <MovieCompaniesPanel
            :movie-id="data.movieId"
            :companies="data.companies"
            :enrichment-status="data.enrichment.companies"
            :editing="activeAction === 'companies'"
            @changed="handleCompaniesChanged"
          />
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
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'
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
const galleryViewer = ref<InstanceType<typeof MediaImageGallery> | null>(null)
const owner = useOwnerAuth()
const config = useRuntimeConfig()
const imageBusy = ref(false)
const imageError = ref('')
const imageMessage = ref('')
const confirmRemoval = ref('')

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

function handleGalleryImageChanged() {
  confirmRemoval.value = ''
  imageError.value = ''
}

async function handleGalleryClosed(needsFocusFallback: boolean) {
  confirmRemoval.value = ''
  imageError.value = ''
  await nextTick()
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

async function manageImage(url: string, remove: boolean) {
  if (!data.value || imageBusy.value) return
  imageBusy.value = true
  imageError.value = ''
  imageMessage.value = ''
  try {
    const result = await $fetch<{ coverUrl: string | null }>(
      `/api/movies/${data.value.movieId}/images${remove ? '' : '/primary'}`,
      { baseURL: config.public.apiBase, method: remove ? 'DELETE' : 'POST', body: { url } },
    )
    data.value.coverUrl = result.coverUrl
    const remaining = data.value.gallery.filter((image) => !remove || image !== url)
    data.value.gallery = result.coverUrl
      ? [result.coverUrl, ...remaining.filter((image) => image !== result.coverUrl)]
      : remaining
    confirmRemoval.value = ''
    imageMessage.value = remove ? 'Imagem removida do filme.' : 'Capa atualizada.'
    await refresh()
  } catch {
    imageError.value = 'Não foi possível concluir a alteração. Tente novamente.'
  } finally {
    imageBusy.value = false
  }
}

function toggleAction(action: MovieAction) {
  activeAction.value = activeAction.value === action ? null : action
}
</script>

<style scoped>
.movie-page {
  display: grid;
  gap: 32px;
  width: min(1200px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.image-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  justify-content: center;
  color: #e5e5e0;
  padding: 8px 0;
}
.image-actions button {
  min-height: 44px;
  padding: 8px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.75rem;
  cursor: pointer;
}
.image-actions button:disabled {
  opacity: 0.5;
  cursor: default;
}
.image-actions button:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
.cover-label {
  font-size: 0.75rem;
  font-weight: 700;
  color: #e5e5e0;
}
.image-feedback {
  flex-basis: 100%;
  margin: 4px 0;
  text-align: center;
  font-size: 0.88rem;
}
.remove-confirmation {
  width: 100%;
  text-align: center;
}
.remove-confirmation button + button {
  margin-left: 8px;
}
.remove-confirmation p {
  font-size: 0.88rem;
}
.image-error {
  color: #ffb4b4;
}

.enrichment-state {
  display: flex;
  align-items: center;
  gap: 8px;
  width: fit-content;
  margin: 0 12px calc(-1 * var(--sema-space-section));
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

.personal-context,
.sessions-section,
.movie-details {
  display: grid;
  gap: 20px;
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
