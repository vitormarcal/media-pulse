<template>
  <main class="show-page">
    <p v-if="status === 'pending' && !data" role="status">Carregando série…</p>
    <p v-else-if="error" role="alert">Não foi possível carregar esta série.</p>
    <template v-else-if="data">
      <MoviePageHero
        back-to="/shows"
        back-label="Voltar para séries"
        :title="data.title"
        :subtitle="heroSubtitle"
        :description="data.description"
        :gallery="data.gallery.slice(0, 4)"
        :image-count="data.gallery.length"
        @view-image="galleryViewer?.open($event)"
      >
        <template v-if="owner.authenticated" #image-actions>
          <MediaImageSelector
            :key="data.showId"
            media-type="shows"
            :entity-id="data.showId"
            :title="data.title"
            compact
            @imported="refresh()"
          />
        </template>
        <template v-if="owner.authenticated || data.favorite || data.abandoned" #personal>
          <MediaPersonalMarks
            :key="data.showId"
            v-model:favorite="data.favorite"
            v-model:abandoned="data.abandoned"
            media-type="shows"
            :entity-id="data.showId"
            @saved="refresh()"
          />
        </template>
      </MoviePageHero>
      <MediaImageGallery ref="galleryViewer" :images="data.gallery" :title="data.title" viewer-only />

      <ShowProgressPanel class="detail-surface" :progress="data.progress" :seasons="data.seasons">
        <template v-if="owner.authenticated" #actions>
          <button class="action" type="button" :disabled="refreshingEpisodes" @click="refreshEpisodes">
            {{ refreshingEpisodes ? 'Atualizando episódios…' : 'Atualizar episódios' }}
          </button>
        </template>
        <p v-if="episodesRefreshMessage" :role="episodesRefreshFailed ? 'alert' : 'status'">
          {{ episodesRefreshMessage }}
        </p>
      </ShowProgressPanel>

      <section
        v-if="owner.authenticated || data.rating != null || data.comments.length || data.recentWatches.length"
        class="detail-surface"
        aria-labelledby="activity-title"
      >
        <div class="section-head">
          <h2 id="activity-title">Atividade</h2>
          <button
            v-if="owner.authenticated"
            class="action"
            type="button"
            :aria-expanded="registering"
            @click="registering = !registering"
          >
            {{ registering ? 'Fechar registro' : 'Registrar sessão' }}
          </button>
        </div>
        <ShowAddWatchPanel v-if="owner.authenticated && registering" :show-id="data.showId" @created="refresh()" />
        <MediaCommentsPanel
          v-if="owner.authenticated || data.comments.length"
          :entity-id="data.showId"
          media-type="shows"
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
              :key="data.showId"
              media-type="shows"
              :entity-id="data.showId"
              :initial-rating="data.rating"
              label="Nota da série"
              compact
              minimal
            />
          </template>
        </MediaCommentsPanel>
        <MediaRatingPanel
          v-else-if="data.rating != null"
          :key="data.showId"
          media-type="shows"
          :entity-id="data.showId"
          :initial-rating="data.rating"
          label="Nota da série"
          compact
          minimal
        />
        <ShowWatchTimeline v-if="data.recentWatches.length" :watches="data.recentWatches" />
      </section>

      <section
        v-if="owner.authenticated || data.people.visibleCount || data.terms.visibleCount || data.lists.length"
        class="detail-surface"
        aria-labelledby="about-title"
      >
        <div class="section-head">
          <h2 id="about-title">Sobre a série</h2>
          <button
            v-if="owner.authenticated"
            type="button"
            class="action"
            :aria-expanded="editingMetadata"
            @click="editingMetadata = !editingMetadata"
          >
            {{ editingMetadata ? 'Fechar atualização' : 'Atualizar dados' }}
          </button>
        </div>
        <ShowMetadataEnrichmentPanel
          v-if="owner.authenticated && editingMetadata"
          :show-id="data.showId"
          :identifiers="data.externalIds"
          @applied="refresh()"
        />

        <div v-if="owner.authenticated || data.people.visibleCount" class="detail-group">
          <button
            v-if="owner.authenticated"
            type="button"
            class="action"
            :aria-expanded="editingPeople"
            @click="editingPeople = !editingPeople"
          >
            {{ editingPeople ? 'Concluir edição' : 'Editar créditos' }}
          </button>
          <ShowPeoplePanel
            :show-id="data.showId"
            :people="data.people"
            :editing="editingPeople && owner.authenticated"
            @changed="refresh"
          />
        </div>

        <div v-if="owner.authenticated || data.terms.visibleCount" class="detail-group">
          <button
            v-if="owner.authenticated"
            type="button"
            class="action"
            :aria-expanded="editingTerms"
            @click="editingTerms = !editingTerms"
          >
            {{ editingTerms ? 'Concluir edição' : 'Editar gêneros e tags' }}
          </button>
          <p v-if="owner.authenticated && data.enrichment.terms.status === 'PENDING'" role="status">
            Buscando gêneros e tags…
          </p>
          <p v-else-if="owner.authenticated && data.enrichment.terms.status === 'RETRY_SCHEDULED'" role="status">
            Gêneros e tags serão atualizados em uma nova tentativa.
          </p>
          <p v-else-if="owner.authenticated && data.enrichment.terms.status === 'BLOCKED'">
            Vincule a série ao TMDb em “Atualizar dados” para buscar gêneros e tags.
          </p>
          <ShowTermsPanel
            v-if="data.terms.visibleCount || editingTerms"
            :show-id="data.showId"
            :terms="data.terms"
            :editing="editingTerms && owner.authenticated"
            :enrichment-status="data.enrichment.terms"
            @changed="refresh"
          />
        </div>

        <div v-if="owner.authenticated || data.lists.length" class="detail-group">
          <button
            v-if="owner.authenticated"
            type="button"
            class="action"
            :aria-expanded="editingLists"
            @click="editingLists = !editingLists"
          >
            {{ editingLists ? 'Concluir edição' : 'Organizar em listas' }}
          </button>
          <ShowListsPanel
            v-if="data.lists.length || editingLists"
            :show-id="data.showId"
            :lists="data.lists"
            :editing="editingLists && owner.authenticated"
            @changed="refresh"
          />
        </div>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import MediaPersonalMarks from '~/components/media/MediaPersonalMarks.vue'
import ShowAddWatchPanel from '~/components/shows/ShowAddWatchPanel.vue'
import MoviePageHero from '~/components/movies/MoviePageHero.vue'
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'
import MediaImageSelector from '~/components/media/MediaImageSelector.vue'
import ShowTermsPanel from '~/components/shows/ShowTermsPanel.vue'
import ShowMetadataEnrichmentPanel from '~/components/shows/ShowMetadataEnrichmentPanel.vue'
import ShowListsPanel from '~/components/shows/ShowListsPanel.vue'
import ShowPeoplePanel from '~/components/shows/ShowPeoplePanel.vue'
import ShowProgressPanel from '~/components/shows/ShowProgressPanel.vue'
import ShowWatchTimeline from '~/components/shows/ShowWatchTimeline.vue'
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import { useShowPageData } from '~/composables/useShowPageData'
import type { ShowEpisodesRefreshResponse } from '~/types/shows'

const owner = useOwnerAuth()

const config = useRuntimeConfig()
const refreshingEpisodes = ref(false)
const episodesRefreshMessage = ref('')
const episodesRefreshFailed = ref(false)
const route = useRoute()
const slug = computed(() => String(route.params.slug))
const galleryViewer = ref<InstanceType<typeof MediaImageGallery> | null>(null)
const editingTerms = ref(false)
const editingPeople = ref(false)
const editingLists = ref(false)
const editingMetadata = ref(false)
const registering = ref(false)
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

async function refreshEpisodes() {
  if (!data.value || refreshingEpisodes.value) return
  episodesRefreshFailed.value = false
  episodesRefreshMessage.value = ''
  if (!data.value.externalIds.some((identifier) => identifier.provider === 'TMDB')) {
    episodesRefreshMessage.value = 'Vincule a série ao TMDb em “Atualizar dados” antes de atualizar episódios.'
    return
  }
  refreshingEpisodes.value = true
  try {
    const result = await $fetch<ShowEpisodesRefreshResponse>(`/api/shows/${data.value.showId}/episodes/refresh`, {
      baseURL: config.public.apiBase,
      method: 'POST',
    })
    episodesRefreshMessage.value = result.addedEpisodesCount
      ? `Atualização concluída: ${result.addedSeasonsCount} novas temporadas e ${result.addedEpisodesCount} novos episódios.`
      : 'Nenhum episódio novo. Todas as temporadas regulares estão atualizadas.'
    await refresh()
  } catch {
    episodesRefreshFailed.value = true
    episodesRefreshMessage.value = 'Não foi possível atualizar os episódios. Tente novamente.'
  } finally {
    refreshingEpisodes.value = false
  }
}
</script>

<style scoped>
.show-page {
  display: grid;
  gap: 32px;
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
  padding: 28px 0 64px;
}
.detail-surface {
  display: grid;
  gap: 20px;
  padding: clamp(20px, 3vw, 32px);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 35%, white);
}
.section-head {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
}
h2,
p {
  margin: 0;
}
h2 {
  font-size: 1.25rem;
}
.detail-group {
  display: grid;
  gap: 12px;
  padding-top: 16px;
  border-top: 1px solid var(--base-color-surface-soft);
}
.action {
  justify-self: start;
  padding: 10px 14px;
  min-height: 44px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.875rem;
  cursor: pointer;
}
.action:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
</style>
