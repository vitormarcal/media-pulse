<template>
  <section class="library-section">
    <SectionHeading
      :eyebrow="eyebrow"
      :title="title"
      :description="description"
      :summary="summary"
    />

    <p v-if="!items.length" class="empty-state">
      {{ emptyMessage || 'Nada para mostrar aqui ainda.' }}
    </p>

    <div v-else class="masonry-grid">
      <MusicLibraryCard v-for="item in items" :key="item.id" :item="item" />
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import MusicLibraryCard from '~/components/music/MusicLibraryCard.vue'
import type { MusicLibraryCardModel } from '~/types/music'

defineProps<{
  eyebrow: string
  title: string
  description: string
  summary: string
  items: MusicLibraryCardModel[]
  emptyMessage?: string
}>()
</script>

<style scoped>
.library-section {
  display: grid;
  gap: 24px;
}

.masonry-grid {
  column-count: 5;
  column-gap: 20px;
}

.empty-state {
  margin: 0;
  color: var(--base-color-text-secondary);
}

@media (max-width: 1440px) {
  .masonry-grid {
    column-count: 4;
  }
}

@media (max-width: 1100px) {
  .masonry-grid {
    column-count: 3;
  }
}

@media (max-width: 780px) {
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
