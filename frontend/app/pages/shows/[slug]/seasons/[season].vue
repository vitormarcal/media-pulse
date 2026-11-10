<template>
  <main class="season-page">
    <p v-if="status === 'pending' && !data" role="status">Carregando temporada…</p>
    <p v-else-if="error" role="alert">Não foi possível carregar esta temporada.</p>
    <template v-else-if="data">
      <NuxtLink class="action back-link" :to="data.showSlug ? `/shows/${data.showSlug}` : '/shows'">
        {{ data.showSlug ? `Voltar para ${data.showTitle}` : 'Voltar para séries' }}
      </NuxtLink>

      <header class="surface season-header">
        <div>
          <p class="muted">
            {{ data.showTitle }}<span v-if="data.showYear"> · {{ data.showYear }}</span>
          </p>
          <h1>{{ data.seasonTitle }}</h1>
        </div>
        <div v-if="data.progress.totalEpisodes" class="progress-summary">
          <p>{{ data.progress.watchedEpisodes }} de {{ data.progress.totalEpisodes }} episódios assistidos</p>
          <progress
            :value="data.progress.watchedEpisodes"
            :max="data.progress.totalEpisodes"
            aria-label="Progresso da temporada"
          />
        </div>
      </header>

      <section class="surface" aria-labelledby="episodes-title">
        <div class="section-head">
          <h2 id="episodes-title">Episódios</h2>
          <button
            v-if="owner.authenticated && data.seasonNumber != null"
            class="action"
            type="button"
            :aria-expanded="enrichmentOpen"
            @click="enrichmentOpen = !enrichmentOpen"
          >
            {{ enrichmentOpen ? 'Fechar atualização' : 'Atualizar dados da temporada' }}
          </button>
        </div>
        <p v-if="watchMessage" :role="watchFailed ? 'alert' : 'status'">{{ watchMessage }}</p>
        <p v-if="!data.episodes.length" class="muted">Nenhum episódio disponível.</p>
        <ol v-else class="episode-list">
          <li v-for="episode in data.episodes" :key="episode.id" class="episode">
            <div class="episode-heading">
              <div>
                <p class="muted">{{ episode.context }}</p>
                <h3>{{ episode.title }}</h3>
              </div>
              <span class="episode-state" :class="{ watched: episode.watched }">{{
                episode.watched ? 'Assistido' : 'Não assistido'
              }}</span>
            </div>
            <p v-if="episode.summary" class="episode-summary">{{ episode.summary }}</p>
            <p v-if="episode.meta.length" class="muted">{{ episode.meta.join(' · ') }}</p>
            <MediaRatingPanel
              v-if="owner.authenticated || episode.rating != null"
              media-type="episodes"
              :entity-id="episode.episodeId"
              :initial-rating="episode.rating"
              compact
              minimal
              label="Nota do episódio"
            />

            <div v-if="owner.authenticated && !episode.watched" class="episode-controls">
              <div class="section-head">
                <button
                  class="action primary"
                  type="button"
                  :disabled="pendingEpisodeId != null"
                  @click="registerWatch(episode, 'now')"
                >
                  {{ pendingEpisodeId === episode.episodeId ? 'Registrando…' : 'Marcar como assistido' }}
                </button>
                <button
                  class="action"
                  type="button"
                  :disabled="pendingEpisodeId != null"
                  :aria-expanded="editingEpisodeId === episode.episodeId"
                  @click="toggleEditor(episode.episodeId)"
                >
                  {{ editingEpisodeId === episode.episodeId ? 'Fechar opções' : 'Opções de registro' }}
                </button>
              </div>
              <form
                v-if="editingEpisodeId === episode.episodeId"
                class="episode-editor"
                @submit.prevent="registerWatch(episode, 'date')"
              >
                <label>
                  <span>Data e hora</span>
                  <input v-model="watchedAtInput" type="datetime-local" required :disabled="pendingEpisodeId != null" />
                </label>
                <div class="section-head">
                  <button class="action" type="submit" :disabled="pendingEpisodeId != null || !watchedAtInput">
                    Registrar nesta data
                  </button>
                  <button
                    class="action"
                    type="button"
                    :disabled="pendingEpisodeId != null || !watchedAtInput"
                    @click="registerWatch(episode, 'until')"
                  >
                    Marcar até este episódio
                  </button>
                </div>
                <p class="muted">A marcação em lote usa esta data para os episódios pendentes até aqui.</p>
              </form>
              <p v-if="failedEpisodeId === episode.episodeId" role="alert">{{ watchError }}</p>
            </div>
          </li>
        </ol>
      </section>

      <ShowSeasonEnrichmentPanel
        v-if="owner.authenticated && enrichmentOpen && data.seasonNumber != null"
        :show-id="data.showId"
        :season-number="data.seasonNumber"
        :initial-tmdb-id="data.showTmdbId"
        @applied="refresh()"
      />
    </template>
  </main>
</template>

<script setup lang="ts">
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import ShowSeasonEnrichmentPanel from '~/components/shows/ShowSeasonEnrichmentPanel.vue'
import { useShowSeasonPageData } from '~/composables/useShowSeasonPageData'
import type { ExistingShowWatchCreateRequest, ShowSeasonEpisodeModel } from '~/types/shows'

const owner = useOwnerAuth()
const route = useRoute()
const config = useRuntimeConfig()
const { data, error, status, refresh } = await useShowSeasonPageData(
  String(route.params.slug),
  Number(route.params.season),
)
const enrichmentOpen = ref(false)
const editingEpisodeId = ref<number | null>(null)
const pendingEpisodeId = ref<number | null>(null)
const failedEpisodeId = ref<number | null>(null)
const watchedAtInput = ref('')
const watchMessage = ref('')
const watchFailed = ref(false)
const watchError = ref('')

useHead(() => ({
  title: data.value ? `${data.value.seasonTitle} · ${data.value.showTitle} · Media Pulse` : 'Temporada · Media Pulse',
}))

function toggleEditor(episodeId: number) {
  editingEpisodeId.value = editingEpisodeId.value === episodeId ? null : episodeId
  const now = new Date()
  watchedAtInput.value = new Date(now.getTime() - now.getTimezoneOffset() * 60000).toISOString().slice(0, 16)
}

async function registerWatch(episode: ShowSeasonEpisodeModel, mode: 'now' | 'date' | 'until') {
  const season = data.value
  if (!season || !owner.value.authenticated || pendingEpisodeId.value != null || episode.watched) return
  watchMessage.value = ''
  watchFailed.value = false
  failedEpisodeId.value = null
  const date = mode === 'now' ? new Date() : new Date(watchedAtInput.value)
  if (Number.isNaN(date.getTime())) {
    failedEpisodeId.value = episode.episodeId
    watchError.value = 'Informe uma data e hora válidas.'
    return
  }

  const index = season.episodes.findIndex((item) => item.episodeId === episode.episodeId)
  if (index < 0) return
  const episodes = mode === 'until' ? season.episodes.slice(0, index + 1).filter((item) => !item.watched) : [episode]
  pendingEpisodeId.value = episode.episodeId
  let saved = 0
  try {
    for (const item of episodes) {
      const body: ExistingShowWatchCreateRequest = {
        watchedAt: date.toISOString(),
        episodeTitle: item.title,
        seasonNumber: season.seasonNumber,
        episodeNumber: item.episodeNumber,
      }
      await $fetch(`/api/shows/${season.showId}/watches`, { baseURL: config.public.apiBase, method: 'POST', body })
      saved++
    }
    editingEpisodeId.value = null
    watchMessage.value =
      saved === 1 ? 'Episódio marcado como assistido.' : `${saved} episódios marcados como assistidos.`
  } catch {
    failedEpisodeId.value = episode.episodeId
    watchError.value =
      mode === 'until'
        ? 'Tente novamente para registrar os episódios pendentes.'
        : 'Não foi possível registrar este episódio. Tente novamente.'
    if (mode === 'until') {
      watchFailed.value = true
      watchMessage.value = `${saved} de ${episodes.length} episódios registrados. Os demais continuam pendentes.`
    }
  } finally {
    // An interrupted batch can have saved a prefix, or the server may have saved a request whose response was lost.
    try {
      await refresh()
    } finally {
      pendingEpisodeId.value = null
    }
  }
}
</script>

<style scoped>
.season-page {
  display: grid;
  gap: 24px;
  width: min(1120px, calc(100% - 32px));
  margin: 0 auto;
  padding: 28px 0 64px;
}
.surface {
  display: grid;
  gap: 20px;
  padding: clamp(20px, 3vw, 32px);
  border-radius: 28px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 35%, white);
  background: var(--base-color-surface-strong);
}
.season-header {
  grid-template-columns: minmax(0, 1fr) minmax(0, 0.65fr);
  align-items: center;
}
.section-head,
.episode-heading {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
}
h1,
h2,
h3,
p,
ol {
  margin: 0;
  overflow-wrap: anywhere;
}
h1 {
  font-size: clamp(1.75rem, 5vw, 3rem);
  line-height: 1.1;
}
h2 {
  font-size: 1.25rem;
}
h3 {
  font-size: 1.125rem;
}
.muted {
  color: var(--base-color-text-secondary);
  font-size: 0.875rem;
}
.progress-summary,
.episode,
.episode-editor,
.episode-controls,
label {
  display: grid;
  gap: 10px;
}
progress {
  width: 100%;
  height: 10px;
  accent-color: var(--base-color-brand-red);
}
.episode-list {
  list-style: none;
  padding: 0;
}
.episode {
  padding: 20px 0;
  border-top: 1px solid var(--base-color-surface-soft);
}
.episode:last-child {
  padding-bottom: 0;
}
.episode-state {
  padding: 4px 8px;
  border-radius: 8px;
  color: var(--base-color-text-secondary);
  background: var(--base-color-surface-soft);
  font-size: 0.8rem;
}
.episode-state.watched {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}
.episode-summary {
  max-width: 68ch;
}
.episode-controls .section-head {
  justify-content: start;
}
.episode-editor {
  padding: 12px;
  background: var(--base-color-surface-soft);
  border-radius: 16px;
}
input {
  width: min(100%, 18rem);
  min-width: 0;
  padding: 8px;
  border: 1px solid var(--base-color-border);
  border-radius: 8px;
  font: inherit;
}
.action {
  display: inline-flex;
  align-items: center;
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
.action.primary {
  background: var(--base-color-brand-red);
  color: white;
}
.action:disabled {
  opacity: 0.6;
  cursor: default;
}
.action:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
.back-link {
  justify-self: start;
}
@media (max-width: 680px) {
  .season-header {
    grid-template-columns: 1fr;
  }
}
</style>
