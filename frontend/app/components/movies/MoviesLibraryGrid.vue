<template>
  <section class="library-section" :class="`library-section--${layout}`">
    <SectionHeading :eyebrow="eyebrow" :title="title" :description="description" :summary="summary" />

    <p v-if="!items.length" class="empty-state">
      {{ emptyMessage || 'Nada para mostrar aqui ainda.' }}
    </p>

    <div v-else class="library-grid" :class="`library-grid--${layout}`">
      <MovieLibraryCard v-for="item in items" :key="item.id" :item="item" />
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import MovieLibraryCard from '~/components/movies/MovieLibraryCard.vue'
import type { MovieLibraryCardModel } from '~/types/movies'

withDefaults(
  defineProps<{
    eyebrow: string
    title: string
    description: string
    summary: string
    items: MovieLibraryCardModel[]
    layout?: 'masonry' | 'aligned'
    emptyMessage?: string
  }>(),
  {
    layout: 'masonry',
  },
)
</script>

<style scoped>
.library-section {
  display: grid;
  gap: 24px;
}

.library-grid--masonry {
  column-count: 4;
  column-gap: 20px;
}

.library-grid--aligned {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px 20px;
  align-items: start;
}

.library-grid--aligned :deep(.library-link) {
  height: 100%;
}

.library-grid--aligned :deep(.library-card) {
  height: 100%;
  margin-bottom: 0;
}

.empty-state {
  margin: 0;
  color: var(--base-color-text-secondary);
}

@media (max-width: 1280px) {
  .library-grid--masonry {
    column-count: 3;
  }

  .library-grid--aligned {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 900px) {
  .library-grid--masonry {
    column-count: 2;
  }

  .library-grid--aligned {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .library-grid--masonry {
    column-count: 1;
  }

  .library-grid--aligned {
    grid-template-columns: 1fr;
  }
}
</style>
