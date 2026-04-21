<template>
  <section class="library-section">
    <SectionHeading :eyebrow="eyebrow" :title="title" :description="description" :summary="summary" />

    <p v-if="!items.length" class="empty-state">
      {{ emptyMessage || 'Nada para mostrar aqui ainda.' }}
    </p>

    <div v-else class="masonry-grid">
      <MovieLibraryCard v-for="item in items" :key="item.id" :item="item" />
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import MovieLibraryCard from '~/components/movies/MovieLibraryCard.vue'
import type { MovieLibraryCardModel } from '~/types/movies'

defineProps<{
  eyebrow: string
  title: string
  description: string
  summary: string
  items: MovieLibraryCardModel[]
  emptyMessage?: string
}>()
</script>

<style scoped>
.library-section {
  display: grid;
  gap: 24px;
}

.masonry-grid {
  column-count: 4;
  column-gap: 20px;
}

.empty-state {
  margin: 0;
  color: var(--base-color-text-secondary);
}

@media (max-width: 1280px) {
  .masonry-grid {
    column-count: 3;
  }
}

@media (max-width: 900px) {
  .masonry-grid {
    column-count: 2;
  }
}

@media (max-width: 520px) {
  .masonry-grid {
    column-count: 1;
  }
}
</style>
