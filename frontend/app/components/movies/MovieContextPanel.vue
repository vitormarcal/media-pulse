<template>
  <section class="context-panel">
    <SectionHeading
      eyebrow="Contexto"
      title="O lugar desse filme no seu histórico"
      description="Uma leitura curta do quanto ele reapareceu no seu histórico."
      summary="Só o bastante para recolocar o filme na memória."
    />

    <div class="panel-grid">
      <article class="stats-card">
        <p class="stats-label">Sessões registradas</p>
        <div class="stats-value">{{ stats.totalWatches }}</div>
        <p class="stats-copy">Última sessão {{ stats.latestWatchRelative }}</p>
        <div class="stats-meta">
          <span v-if="stats.firstWatch">Primeira: {{ formatAbsoluteDate(stats.firstWatch) }}</span>
          <span v-if="stats.latestWatch">Última: {{ formatAbsoluteDate(stats.latestWatch) }}</span>
        </div>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { MoviePageData } from '~/types/movies'
import { formatAbsoluteDate } from '~/utils/formatting'

defineProps<{
  stats: MoviePageData['stats']
}>()
</script>

<style scoped>
.context-panel {
  display: grid;
  gap: 24px;
}

.panel-grid {
  display: grid;
  grid-template-columns: minmax(18rem, 0.9fr);
  gap: 22px;
}

.stats-card {
  display: grid;
  gap: 12px;
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.stats-label {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.76rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.stats-value {
  font-size: clamp(3rem, 7vw, 4.6rem);
  line-height: 0.9;
  letter-spacing: -0.07em;
}

.stats-copy,
.stats-meta {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.stats-meta {
  display: grid;
  gap: 8px;
  font-size: 0.88rem;
}

@media (max-width: 980px) {
  .panel-grid {
    grid-template-columns: 1fr;
  }
}
</style>
