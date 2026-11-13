<template>
  <section class="library-section" :class="`library-section--${layout}`">
    <div v-if="refined" class="library-heading">
      <h2>{{ title }}</h2>
      <p v-if="summary" class="library-context">{{ summary }}</p>
    </div>
    <SectionHeading v-else :eyebrow="eyebrow" :title="title" :description="description" :summary="summary" />

    <p v-if="!items.length" class="empty-state">
      {{ emptyMessage || 'Nada para mostrar aqui ainda.' }}
    </p>

    <div v-else class="library-grid" :class="[`library-grid--${layout}`, { 'library-grid--refined': refined }]">
      <MovieLibraryCard v-for="item in items" :key="item.id" :item="item" :refined="refined" />
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
    refined?: boolean
    layout?: 'masonry' | 'aligned'
    emptyMessage?: string
  }>(),
  {
    layout: 'masonry',
    emptyMessage: '',
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
h2 {
  margin: 0;
  font-size: 1.75rem;
  letter-spacing: -0.04em;
}
.library-grid--aligned.library-grid--refined :deep(.library-card) {
  align-content: start;
  grid-template-rows: auto auto;
}
.library-grid--aligned.library-grid--refined :deep(.poster) {
  aspect-ratio: 2 / 3;
  display: grid;
  place-items: center;
}
.library-grid--aligned.library-grid--refined :deep(.poster img) {
  width: 100%;
  height: 100%;
  object-fit: contain;
}
.library-grid--aligned.library-grid--refined :deep(.poster .media-image-fallback) {
  width: 100%;
  height: 100%;
  box-sizing: border-box;
  display: grid;
  place-items: center;
}
@media (max-width: 520px) {
  .library-grid--aligned.library-grid--refined {
    grid-template-columns: repeat(2, minmax(0, 1fr));
    gap: 24px 16px;
  }
}
@media (max-width: 340px) {
  .library-grid--aligned.library-grid--refined {
    grid-template-columns: minmax(0, 1fr);
  }
}
.library-heading {
  display: grid;
  gap: 8px;
}
.library-context {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.9rem;
}
</style>
