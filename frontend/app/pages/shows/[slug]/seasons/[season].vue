<template>
  <main class="season-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a temporada...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar esta temporada.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <section class="season-hero">
        <NuxtLink class="back-link" :to="data.showSlug ? `/shows/${data.showSlug}` : '/shows'">
          Voltar para série
        </NuxtLink>

        <div class="hero-grid">
          <div class="copy">
            <NuxtLink class="show-title-link" :to="data.showSlug ? `/shows/${data.showSlug}` : '/shows'">
              {{ data.showTitle }}
            </NuxtLink>
            <h1>{{ data.seasonTitle }}</h1>
            <p class="subtitle">{{ heroSubtitle }}</p>

            <div class="meta-list">
              <span v-for="item in data.heroMeta" :key="item" class="meta-pill">{{ item }}</span>
            </div>
          </div>

          <div class="progress-card">
            <p class="overview-label">Progresso da temporada</p>
            <div class="overview-value">{{ data.progress.completionPct }}%</div>
            <p class="overview-copy">{{ data.progress.statusText }}</p>
            <div class="season-bar">
              <span class="season-bar-fill" :style="{ width: `${data.progress.completionPct}%` }" />
            </div>
            <div class="overview-meta">
              <span>{{ data.progress.watchedEpisodes }}/{{ data.progress.totalEpisodes }} episódios</span>
              <span>{{ data.progress.lastWatchedLabel }}</span>
            </div>
          </div>
        </div>
      </section>

      <ShowSeasonEnrichmentPanel
        v-if="data.seasonNumber != null"
        :show-id="data.showId"
        :season-number="data.seasonNumber"
        :initial-tmdb-id="data.showTmdbId"
        @applied="handleEnrichmentApplied"
      />

      <section class="episodes-section">
        <SectionHeading
          eyebrow="Episódios"
          title="A temporada em ordem"
          description="Cada episódio aparece com o estado de watch e as informações que já chegaram do catálogo."
          summary="Aqui a temporada deixa de ser só porcentagem e vira uma lista navegável."
        />

        <div class="episode-list">
          <article
            v-for="episode in data.episodes"
            :key="episode.id"
            class="episode-card"
            :class="{ watched: episode.watched }"
          >
            <div class="episode-number">{{ episode.context }}</div>
            <div class="episode-copy">
              <div class="episode-header">
                <div>
                  <h2>{{ episode.title }}</h2>
                  <span class="watch-badge" :class="{ watched: episode.watched }">{{ episode.watchedLabel }}</span>
                </div>
                <div v-if="!episode.watched" class="episode-actions">
                  <button
                    type="button"
                    class="secondary-button"
                    :disabled="isEpisodeSubmitting(episode.episodeId)"
                    @click="toggleEditor(episode.episodeId)"
                  >
                    Data
                  </button>
                  <button
                    type="button"
                    class="secondary-button"
                    :disabled="isEpisodeSubmitting(episode.episodeId)"
                    @click="markUntilEpisode(episode)"
                  >
                    {{ submittingUntilEpisodeId === episode.episodeId ? 'Salvando...' : 'Até aqui' }}
                  </button>
                  <button
                    type="button"
                    class="primary-button"
                    :disabled="isEpisodeSubmitting(episode.episodeId)"
                    @click="markEpisode(episode)"
                  >
                    {{ submittingEpisodeId === episode.episodeId ? 'Salvando...' : 'Marcar' }}
                  </button>
                </div>
              </div>
              <p v-if="episode.summary" class="episode-summary">{{ episode.summary }}</p>
              <div v-if="episode.meta.length" class="episode-meta">
                <span v-for="item in episode.meta" :key="item">{{ item }}</span>
              </div>
              <div v-if="editingEpisodeId === episode.episodeId && !episode.watched" class="episode-date-row">
                <label>
                  <span>Data e hora</span>
                  <input v-model="watchedAtInput" type="datetime-local" />
                </label>
              </div>
            </div>
          </article>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import ShowSeasonEnrichmentPanel from '~/components/shows/ShowSeasonEnrichmentPanel.vue'
import { useShowSeasonPageData } from '~/composables/useShowSeasonPageData'
import type {
  ExistingShowWatchCreateRequest,
  ShowSeasonEnrichmentApplyResponse,
  ShowSeasonEpisodeModel,
} from '~/types/shows'

const route = useRoute()
const slug = computed(() => String(route.params.slug))
const seasonNumber = computed(() => Number(route.params.season))

const config = useRuntimeConfig()
const { data, error, status, refresh } = await useShowSeasonPageData(slug.value, seasonNumber.value)
const editingEpisodeId = ref<number | null>(null)
const submittingEpisodeId = ref<number | null>(null)
const submittingUntilEpisodeId = ref<number | null>(null)
const watchedAtInput = ref(toDatetimeLocalValue(new Date()))

const heroSubtitle = computed(() => {
  if (!data.value) return ''

  if (data.value.showOriginalTitle !== data.value.showTitle && data.value.showYear) {
    return `${data.value.showOriginalTitle} · ${data.value.showYear}`
  }

  if (data.value.showOriginalTitle !== data.value.showTitle) {
    return data.value.showOriginalTitle
  }

  return data.value.showYear ? String(data.value.showYear) : 'Série'
})

useHead(() => ({
  title: data.value ? `${data.value.seasonTitle} · ${data.value.showTitle} · Media Pulse` : 'Temporada · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value
        ? `Temporada de ${data.value.showTitle} no Media Pulse.`
        : 'Página interna de temporada no Media Pulse.',
    },
  ],
}))

function toDatetimeLocalValue(date: Date) {
  const offset = date.getTimezoneOffset()
  const localDate = new Date(date.getTime() - offset * 60000)
  return localDate.toISOString().slice(0, 16)
}

function toggleEditor(episodeId: number) {
  editingEpisodeId.value = editingEpisodeId.value === episodeId ? null : episodeId
  watchedAtInput.value = toDatetimeLocalValue(new Date())
}

function isEpisodeSubmitting(episodeId: number) {
  return submittingUntilEpisodeId.value != null || submittingEpisodeId.value === episodeId
}

function episodesUntil(targetEpisode: ShowSeasonEpisodeModel) {
  if (!data.value) return []

  const targetIndex = data.value.episodes.findIndex((episode) => episode.episodeId === targetEpisode.episodeId)
  if (targetIndex < 0) return []

  return data.value.episodes.slice(0, targetIndex + 1).filter((episode) => !episode.watched)
}

function buildWatchRequest(episode: ShowSeasonEpisodeModel): ExistingShowWatchCreateRequest {
  if (!data.value) {
    throw new Error('Temporada indisponível')
  }

  return {
    watchedAt: new Date(watchedAtInput.value).toISOString(),
    episodeTitle: episode.title,
    seasonNumber: data.value.seasonNumber,
    episodeNumber: episode.episodeNumber,
  }
}

async function submitEpisodeWatch(episode: ShowSeasonEpisodeModel) {
  if (!data.value) return

  await $fetch(`/api/shows/${data.value.showId}/watches`, {
    baseURL: config.public.apiBase,
    method: 'POST',
    body: buildWatchRequest(episode),
  })
}

async function markEpisode(episode: ShowSeasonEpisodeModel, refreshAfterSubmit = true) {
  if (!data.value) return

  submittingEpisodeId.value = episode.episodeId

  try {
    await submitEpisodeWatch(episode)

    editingEpisodeId.value = null
    watchedAtInput.value = toDatetimeLocalValue(new Date())
    if (refreshAfterSubmit) {
      await refresh()
    }
  } finally {
    submittingEpisodeId.value = null
  }
}

async function markUntilEpisode(episode: ShowSeasonEpisodeModel) {
  if (!data.value) return

  const pendingEpisodes = episodesUntil(episode)
  if (!pendingEpisodes.length) return

  submittingUntilEpisodeId.value = episode.episodeId
  submittingEpisodeId.value = episode.episodeId

  try {
    for (const pendingEpisode of pendingEpisodes) {
      await submitEpisodeWatch(pendingEpisode)
    }

    editingEpisodeId.value = null
    watchedAtInput.value = toDatetimeLocalValue(new Date())
    await refresh()
  } finally {
    submittingUntilEpisodeId.value = null
    submittingEpisodeId.value = null
  }
}

async function handleEnrichmentApplied(_response: ShowSeasonEnrichmentApplyResponse) {
  await refresh()
}
</script>

<style scoped>
.season-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.season-hero {
  display: grid;
  gap: 18px;
}

.back-link {
  width: fit-content;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.05fr) minmax(20rem, 0.95fr);
  gap: 24px;
  padding: clamp(24px, 4vw, 42px);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  border-radius: 40px;
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.98));
}

.copy,
.progress-card,
.episode-list,
.episode-copy {
  display: grid;
}

.copy {
  align-content: end;
  gap: 12px;
}

.eyebrow,
.subtitle,
.overview-label,
.overview-copy,
.overview-meta,
.episode-summary,
.episode-meta {
  margin: 0;
}

.show-title-link,
.overview-label {
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

.show-title-link {
  width: fit-content;
}

.show-title-link:hover {
  text-decoration: underline;
  text-decoration-thickness: 2px;
  text-underline-offset: 4px;
}

h1 {
  margin: 0;
  font-size: clamp(3rem, 7vw, 5.8rem);
  line-height: 0.92;
  letter-spacing: -0.07em;
}

.subtitle,
.overview-copy,
.overview-meta,
.episode-summary,
.episode-meta {
  color: var(--base-color-text-secondary);
}

.meta-list,
.episode-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.meta-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.progress-card {
  align-content: center;
  gap: 14px;
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.overview-value {
  font-size: clamp(3rem, 7vw, 4.6rem);
  line-height: 0.9;
  letter-spacing: -0.07em;
}

.overview-meta {
  gap: 8px;
  font-size: 0.88rem;
}

.season-bar {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 74%, white);
}

.season-bar-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #e60023, #f15f6e);
}

.episodes-section,
.episode-list {
  display: grid;
  gap: 24px;
}

.episode-card {
  display: grid;
  grid-template-columns: 5rem minmax(0, 1fr);
  gap: 18px;
  padding: 18px 20px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.episode-card.watched {
  background: color-mix(in srgb, var(--base-color-brand-red) 7%, var(--base-color-surface-strong));
}

.episode-number {
  display: grid;
  place-items: center;
  width: 4rem;
  height: 4rem;
  border-radius: 50%;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.9rem;
  font-weight: 700;
}

.episode-copy {
  gap: 10px;
}

.episode-header {
  display: flex;
  align-items: start;
  justify-content: space-between;
  gap: 16px;
}

h2 {
  margin: 0;
  font-size: 1.2rem;
  line-height: 1.08;
}

.watch-badge {
  display: inline-flex;
  width: fit-content;
  margin-top: 8px;
  padding: 7px 10px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.78rem;
}

.watch-badge.watched {
  background: color-mix(in srgb, var(--base-color-brand-red) 14%, white);
}

.episode-actions {
  display: flex;
  flex: 0 0 auto;
  gap: 10px;
}

.primary-button,
.secondary-button {
  min-height: 38px;
  border: 0;
  border-radius: 16px;
  padding: 9px 13px;
  cursor: pointer;
  font-size: 0.82rem;
  white-space: nowrap;
}

.primary-button {
  background: var(--base-color-brand-red);
  color: #000000;
}

.secondary-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.primary-button:disabled,
.secondary-button:disabled {
  cursor: not-allowed;
  opacity: 0.58;
}

.episode-date-row {
  display: flex;
  justify-content: flex-end;
}

.episode-date-row label {
  display: grid;
  width: min(100%, 18rem);
  gap: 8px;
}

.episode-date-row span {
  color: var(--base-color-text-secondary);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.episode-date-row input {
  width: 100%;
  padding: 11px 13px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
}

.episode-summary {
  max-width: 62rem;
  line-height: 1.6;
}

.episode-meta {
  font-size: 0.84rem;
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

@media (max-width: 980px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .season-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }

  .episode-card {
    grid-template-columns: 1fr;
  }

  .episode-header {
    flex-direction: column;
  }

  .episode-actions,
  .episode-date-row {
    justify-content: flex-start;
  }
}
</style>
