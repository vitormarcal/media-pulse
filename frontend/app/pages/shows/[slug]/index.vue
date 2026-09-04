<template>
  <main class="show-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Carregando...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar esta série.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <div v-if="data.enrichment.terms.status === 'PENDING'" class="enrichment-state" role="status">
        <span class="enrichment-dot" aria-hidden="true" />
        <span>Completando gêneros e tags…</span>
      </div>

      <div
        v-else-if="data.enrichment.terms.status === 'RETRY_SCHEDULED'"
        class="enrichment-state blocked"
        role="status"
      >
        <span>As marcações não puderam ser atualizadas. Tentaremos novamente mais tarde.</span>
      </div>

      <div v-else-if="data.enrichment.terms.status === 'BLOCKED'" class="enrichment-state blocked" role="status">
        <span>Esta série ainda não possui vínculo TMDb para buscar gêneros e tags.</span>
      </div>

      <div ref="heroTarget">
        <ShowPageHero
          :show-id="data.showId"
          :editing-terms="activeAction === 'terms'"
          :title="data.title"
          :subtitle="heroSubtitle"
          :description="data.description"
          :gallery="data.gallery"
          :hero-meta="data.heroMeta"
          :terms="data.terms"
          :enrichment="data.enrichment"
          @terms-changed="refresh"
        />
      </div>

      <section class="show-actions" aria-labelledby="show-actions-title">
        <div class="actions-copy">
          <p class="actions-eyebrow">Curadoria</p>
          <h2 id="show-actions-title">Ações da série</h2>
          <p>Revise marcações ou escolha uma temporada para completar.</p>
        </div>
        <div class="action-list">
          <button
            v-for="action in showActions"
            :key="action.id"
            type="button"
            class="action-button"
            :class="{ active: action.toggle && activeAction === action.id }"
            :aria-pressed="action.toggle ? activeAction === action.id : undefined"
            @click="toggleAction(action.id)"
          >
            <span>{{ action.label }}</span>
            <small>{{ action.toggle && activeAction === action.id ? 'Fechar' : action.description }}</small>
          </button>
        </div>
      </section>

      <div v-if="activeAction === 'metadata'" ref="metadataTarget">
        <ShowMetadataEnrichmentPanel :show-id="data.showId" :identifiers="data.externalIds" @applied="refresh" />
      </div>

      <ShowPeoplePanel :people="data.people" />

      <div ref="seasonsTarget">
        <ShowProgressPanel :progress="data.progress" :seasons="data.seasons" />
      </div>

      <MediaRatingPanel
        media-type="shows"
        :entity-id="data.showId"
        :initial-rating="data.rating"
        title="Nota da série"
        minimal
      />

      <ShowAddWatchPanel :show-id="data.showId" @created="handleWatchCreated" />

      <MediaCommentsPanel
        :entity-id="data.showId"
        media-type="shows"
        title="Comentários da série"
        description=""
        :comments="data.comments"
        empty-label="Nenhum comentário."
      />

      <ShowWatchTimeline :watches="data.recentWatches" />
    </template>
  </main>
</template>

<script setup lang="ts">
import ShowAddWatchPanel from '~/components/shows/ShowAddWatchPanel.vue'
import ShowPageHero from '~/components/shows/ShowPageHero.vue'
import ShowMetadataEnrichmentPanel from '~/components/shows/ShowMetadataEnrichmentPanel.vue'
import ShowPeoplePanel from '~/components/shows/ShowPeoplePanel.vue'
import ShowProgressPanel from '~/components/shows/ShowProgressPanel.vue'
import ShowWatchTimeline from '~/components/shows/ShowWatchTimeline.vue'
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import { useShowPageData } from '~/composables/useShowPageData'
import type { ManualShowWatchCreateResponse } from '~/types/shows'

const route = useRoute()
const slug = computed(() => String(route.params.slug))
type ShowAction = 'terms' | 'metadata' | 'seasons'
const activeAction = ref<ShowAction | null>(null)
const heroTarget = ref<HTMLElement | null>(null)
const seasonsTarget = ref<HTMLElement | null>(null)
const metadataTarget = ref<HTMLElement | null>(null)
const showActions: Array<{ id: ShowAction; label: string; description: string; toggle: boolean }> = [
  { id: 'terms', label: 'Editar gêneros e tags', description: 'Revisar marcações', toggle: true },
  { id: 'metadata', label: 'Enriquecer dados', description: 'Consultar TMDb', toggle: true },
  { id: 'seasons', label: 'Revisar temporadas', description: 'Ver progresso e episódios', toggle: false },
]

const { data, error, status, refresh } = await useShowPageData(slug.value)

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
  title: data.value ? `${data.value.title} · Media Pulse` : 'Série · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value?.description || 'Página interna de série no Media Pulse.',
    },
  ],
}))

async function handleWatchCreated(_response: ManualShowWatchCreateResponse) {
  await refresh()
}

async function toggleAction(action: ShowAction) {
  if (action === 'seasons') {
    activeAction.value = null
    await nextTick()
    seasonsTarget.value?.scrollIntoView({ behavior: 'smooth', block: 'start' })
    return
  }

  activeAction.value = activeAction.value === action ? null : action
  if (!activeAction.value) return
  await nextTick()
  const target = action === 'metadata' ? metadataTarget.value : heroTarget.value
  target?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}
</script>

<style scoped>
.show-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
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

.show-actions {
  display: grid;
  grid-template-columns: minmax(12rem, 0.34fr) minmax(0, 1fr);
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
  grid-template-columns: repeat(2, minmax(0, 1fr));
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

@media (max-width: 720px) {
  .show-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
  .show-actions {
    grid-template-columns: 1fr;
    padding: 18px;
  }
}

@media (max-width: 420px) {
  .action-list {
    grid-template-columns: 1fr;
  }
}
</style>
