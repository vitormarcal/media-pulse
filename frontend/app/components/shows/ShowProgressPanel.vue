<template>
  <section aria-labelledby="show-progress-title">
    <div class="progress-head">
      <h2 id="show-progress-title">Progresso e temporadas</h2>
      <slot name="actions" />
    </div>

    <div v-if="progress.totalEpisodes" class="overview">
      <p>{{ progress.watchedEpisodes }} de {{ progress.totalEpisodes }} episódios assistidos</p>
      <progress :value="progress.watchedEpisodes" :max="progress.totalEpisodes" aria-label="Progresso da série" />
    </div>
    <slot />

    <ul v-if="seasons.length" class="seasons">
      <li v-for="season in seasons" :key="season.id">
        <component :is="season.href ? NuxtLink : 'div'" :to="season.href || undefined" class="season-row">
          <strong>{{ season.title }}</strong>
          <span>{{ season.progressLabel }}</span>
          <span v-if="season.href" aria-hidden="true">›</span>
        </component>
      </li>
    </ul>
    <p v-else>Nenhuma temporada disponível.</p>
  </section>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import type { ShowPageData } from '~/types/shows'

defineProps<{
  progress: ShowPageData['progress']
  seasons: ShowPageData['seasons']
}>()
</script>

<style scoped>
.progress-head,
.season-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  flex-wrap: wrap;
  gap: 8px 16px;
}
h2,
p,
ul {
  margin: 0;
}
h2 {
  font-size: 1.25rem;
}
.overview {
  display: grid;
  gap: 8px;
}
progress {
  width: 100%;
  height: 10px;
  accent-color: var(--base-color-brand-red);
}
.seasons {
  list-style: none;
  padding: 0;
}
.season-row {
  padding: 12px 0;
  min-height: 44px;
  border-top: 1px solid var(--base-color-surface-soft);
  color: var(--base-color-text-primary);
}
.season-row strong {
  flex: 1 1 12rem;
  overflow-wrap: anywhere;
}
.season-row span {
  color: var(--base-color-text-secondary);
}
a.season-row:hover strong {
  text-decoration: underline;
}
a.season-row:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
</style>
