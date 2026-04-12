<template>
  <section class="context-panel">
    <SectionHeading
      eyebrow="Contexto"
      title="Como esse disco apareceu no seu recorte recente"
      description="O essencial para situar o peso do álbum sem transformar a página em painel."
      summary="A repetição e a ordem das faixas dizem mais do que um número isolado."
    />

    <div class="panel-grid">
      <article class="stats-card">
        <p class="stats-label">Plays registrados</p>
        <div class="stats-value">{{ stats.totalPlays }}</div>
        <p class="stats-copy">{{ stats.tracksCount }} faixas no álbum</p>
        <div class="stats-meta">
          <span>Último play {{ stats.latestPlay }}</span>
          <span v-if="stats.latestPlayAbsolute">{{ stats.latestPlayAbsolute }}</span>
        </div>
      </article>

      <article class="days-card">
        <p class="days-label">Dias com play</p>
        <div v-if="recentDays.length" class="days-list">
          <div v-for="day in recentDays" :key="day.id" class="day-row">
            <span>{{ day.label }}</span>
            <strong>{{ day.plays }}</strong>
          </div>
        </div>
        <p v-else class="days-empty">Ainda não há recorte diário suficiente para mostrar aqui.</p>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { AlbumPageData } from '~/types/music'

defineProps<{
  stats: AlbumPageData['stats']
  recentDays: AlbumPageData['recentDays']
}>()
</script>

<style scoped>
.context-panel {
  display: grid;
  gap: 24px;
}

.panel-grid {
  display: grid;
  grid-template-columns: minmax(18rem, 0.9fr) minmax(0, 1.2fr);
  gap: 22px;
}

.stats-card,
.days-card {
  display: grid;
  gap: 12px;
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.stats-label,
.days-label {
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
.stats-meta,
.days-empty {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.stats-meta {
  display: grid;
  gap: 8px;
  font-size: 0.88rem;
}

.days-list {
  display: grid;
  gap: 10px;
}

.day-row {
  display: flex;
  justify-content: space-between;
  gap: 16px;
  padding: 12px 14px;
  border-radius: 16px;
  background: rgba(255, 255, 255, 0.72);
}

.day-row span {
  color: var(--base-color-text-secondary);
}

.day-row strong {
  color: var(--base-color-text-primary);
}

@media (max-width: 980px) {
  .panel-grid {
    grid-template-columns: 1fr;
  }
}
</style>
