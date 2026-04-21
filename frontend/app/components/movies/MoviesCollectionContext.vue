<template>
  <section class="movies-context">
    <SectionHeading :eyebrow="eyebrow" :title="title" :description="description" :summary="summary" />

    <div class="metrics-grid">
      <article v-for="metric in metrics" :key="metric.id" class="metric-card">
        <p class="metric-label">{{ metric.label }}</p>
        <div class="metric-value">{{ metric.value }}</div>
        <p class="metric-note">{{ metric.note }}</p>
      </article>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { MovieCollectionContextMetric } from '~/types/movies'

defineProps<{
  eyebrow: string
  title: string
  description: string
  summary: string
  metrics: MovieCollectionContextMetric[]
}>()
</script>

<style scoped>
.movies-context,
.metrics-grid {
  display: grid;
  gap: 22px;
}

.metrics-grid {
  grid-template-columns: repeat(4, minmax(0, 1fr));
}

.metric-card {
  display: grid;
  gap: 8px;
  padding: 22px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.metric-label,
.metric-note {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.metric-label {
  font-size: 0.8rem;
  line-height: 1.45;
}

.metric-value {
  font-size: clamp(2.2rem, 5vw, 3.4rem);
  line-height: 0.92;
  letter-spacing: -0.07em;
}

.metric-note {
  color: var(--base-color-text-muted);
  font-size: 0.8rem;
  line-height: 1.5;
}

@media (max-width: 1080px) {
  .metrics-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 620px) {
  .metrics-grid {
    grid-template-columns: 1fr;
  }
}
</style>
