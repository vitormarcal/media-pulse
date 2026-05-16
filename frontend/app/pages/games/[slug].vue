<template>
  <main class="game-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página do game...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar este game.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <GamePageHero
        :title="data.title"
        :subtitle="heroSubtitle"
        :description="data.description"
        :gallery="data.gallery"
        :hero-meta="data.heroMeta"
        :identifiers="data.identifiers"
      />

      <section class="context-grid">
        <article class="context-card">
          <p>Sessões</p>
          <strong>{{ data.stats.totalSessions }}</strong>
          <span>{{ data.stats.latestSessionRelative }}</span>
        </article>
      </section>

      <MediaRatingPanel
        media-type="games"
        :entity-id="data.gameId"
        :initial-rating="data.rating"
        title="Quanto esse game ficou na sua escala"
        description="A nota ajuda a separar backlog curioso de jogo que realmente ocupou espaço no repertório."
      />

      <GameAddSessionPanel :game-id="data.gameId" @created="handleSessionChanged" />

      <MediaCommentsPanel
        :entity-id="data.gameId"
        media-type="games"
        title="Impressões do game"
        description="Cada campanha, retorno ou abandono pode render uma leitura diferente sem apagar as anteriores."
        :comments="data.comments"
        empty-label="Nenhuma impressão manual registrada para este game ainda."
      />

      <GameSessionsTimeline :game-id="data.gameId" :sessions="data.sessions" @changed="handleSessionChanged" />
    </template>
  </main>
</template>

<script setup lang="ts">
import GameAddSessionPanel from '~/components/games/GameAddSessionPanel.vue'
import GamePageHero from '~/components/games/GamePageHero.vue'
import GameSessionsTimeline from '~/components/games/GameSessionsTimeline.vue'
import MediaCommentsPanel from '~/components/media/MediaCommentsPanel.vue'
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import { useGamePageData } from '~/composables/useGamePageData'

const route = useRoute()
const slug = computed(() => String(route.params.slug))

const { data, error, status, refresh } = await useGamePageData(slug.value)

const heroSubtitle = computed(() => {
  if (!data.value) return null
  if (data.value.originalTitle !== data.value.title && data.value.year) {
    return `${data.value.originalTitle} · ${data.value.year}`
  }
  if (data.value.originalTitle !== data.value.title) return data.value.originalTitle
  return data.value.year ? String(data.value.year) : null
})

async function handleSessionChanged() {
  await refresh()
}

useHead(() => ({
  title: data.value ? `${data.value.title} · Media Pulse` : 'Game · Media Pulse',
  meta: [{ name: 'description', content: data.value?.description || 'Página interna de game no Media Pulse.' }],
}))
</script>

<style scoped>
.game-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.state-card,
.context-card {
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--base-color-text-secondary);
}

.state-card.error {
  color: #7a1414;
}

.context-grid {
  display: grid;
  grid-template-columns: repeat(3, minmax(0, 1fr));
  gap: 14px;
}

.context-card {
  display: grid;
  gap: 8px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.context-card p,
.context-card span {
  margin: 0;
}

.context-card strong {
  color: var(--base-color-text-primary);
  font-size: 1.8rem;
}

pre {
  margin: 12px 0 0;
  white-space: pre-wrap;
}

@media (max-width: 720px) {
  .game-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }

  .context-grid {
    grid-template-columns: 1fr;
  }
}
</style>
