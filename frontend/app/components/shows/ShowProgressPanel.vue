<template>
  <section class="progress-panel">
    <SectionHeading
      eyebrow="Andamento"
      title="Onde a série ficou"
      description="Um recorte do quanto já andou e de quais temporadas ainda continuam abertas."
      summary="Aqui o progresso serve só para te recolocar no ponto certo."
    />

    <div class="panel-grid">
      <article class="overview-card">
        <p class="overview-label">Progresso geral</p>
        <div class="overview-value">{{ progress.completionPct }}%</div>
        <p class="overview-copy">{{ progress.statusText }}</p>
        <div class="overview-meta">
          <span>{{ progress.watchedEpisodes }}/{{ progress.totalEpisodes }} episódios</span>
          <span>{{ progress.watchedSeasons }}/{{ progress.totalSeasons }} temporadas fechadas</span>
        </div>
      </article>

      <div class="season-grid">
        <NuxtLink
          v-for="season in seasons"
          :key="season.id"
          class="season-card"
          :class="{ disabled: !season.href }"
          :to="season.href || '#'"
          :aria-disabled="!season.href"
        >
          <article>
            <div class="season-header">
              <h3>{{ season.title }}</h3>
              <span class="season-badge" :class="{ complete: season.isComplete }">
                {{ season.progressValue }}%
              </span>
            </div>
            <p class="season-meta">{{ season.progressLabel }}</p>
            <div class="season-bar">
              <span class="season-bar-fill" :style="{ width: `${season.progressValue}%` }" />
            </div>
            <p class="season-detail">{{ season.detail }}</p>
          </article>
        </NuxtLink>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { ShowPageData } from '~/types/shows'

defineProps<{
  progress: ShowPageData['progress']
  seasons: ShowPageData['seasons']
}>()
</script>

<style scoped>
.progress-panel {
  display: grid;
  gap: 24px;
}

.panel-grid {
  display: grid;
  grid-template-columns: minmax(18rem, 0.9fr) minmax(0, 1.3fr);
  gap: 22px;
}

.overview-card,
.season-card {
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
  border-radius: 28px;
}

.overview-card {
  display: grid;
  align-content: start;
  gap: 10px;
  padding: 24px;
}

.overview-label,
.overview-copy,
.overview-meta,
.season-meta,
.season-detail {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.overview-label {
  font-size: 0.76rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--base-color-brand-red);
}

.overview-value {
  font-size: clamp(3rem, 7vw, 4.6rem);
  line-height: 0.9;
  letter-spacing: -0.07em;
}

.overview-meta {
  display: grid;
  gap: 8px;
  font-size: 0.88rem;
}

.season-grid {
  display: grid;
  grid-template-columns: repeat(2, minmax(0, 1fr));
  gap: 18px;
}

.season-card {
  color: inherit;
  text-decoration: none;
}

.season-card article {
  display: grid;
  gap: 12px;
  padding: 20px;
  height: 100%;
  border-radius: inherit;
  transition:
    background 160ms ease,
    transform 160ms ease;
}

.season-card:not(.disabled):hover article {
  background: color-mix(in srgb, var(--base-color-surface-warm) 34%, transparent);
  transform: translateY(-2px);
}

.season-card.disabled {
  pointer-events: none;
}

.season-header {
  display: flex;
  justify-content: space-between;
  gap: 14px;
  align-items: start;
}

.season-header h3 {
  margin: 0;
  font-size: 1.16rem;
  line-height: 1.02;
}

.season-badge {
  padding: 6px 10px;
  border-radius: 999px;
  background: var(--base-color-surface-warm);
  font-size: 0.78rem;
}

.season-badge.complete {
  background: color-mix(in srgb, var(--base-color-brand-red) 14%, white);
}

.season-meta,
.season-detail {
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

@media (max-width: 980px) {
  .panel-grid,
  .season-grid {
    grid-template-columns: 1fr;
  }
}
</style>
