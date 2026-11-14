<template>
  <section class="library-section" :class="{ refined }">
    <header v-if="refined">
      <h2>{{ title }}</h2>
      <p v-if="summary">{{ summary }}</p>
    </header>
    <SectionHeading v-else :eyebrow="eyebrow" :title="title" :description="description" :summary="summary" />

    <p v-if="!items.length" class="empty-state">
      {{ emptyMessage || 'Nada para mostrar aqui ainda.' }}
    </p>

    <div v-else class="masonry-grid">
      <ShowLibraryCard v-for="item in items" :key="item.id" :item="item" :refined="refined" />
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { ShowLibraryCardModel } from '~/types/shows'
import ShowLibraryCard from '~/components/shows/ShowLibraryCard.vue'

defineProps<{
  eyebrow: string
  title: string
  description: string
  summary: string
  items: ShowLibraryCardModel[]
  emptyMessage?: string
  refined?: boolean
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

.refined header h2 {
  margin: 0;
  font-size: 1.6rem;
  letter-spacing: -0.04em;
}
.refined header p {
  margin: 8px 0 0;
  color: var(--base-color-text-secondary);
  font-size: 0.9rem;
}
.refined .masonry-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 24px 20px;
}
@media (max-width: 1280px) {
  .refined .masonry-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
@media (max-width: 900px) {
  .refined .masonry-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}
@media (max-width: 340px) {
  .refined .masonry-grid {
    grid-template-columns: minmax(0, 1fr);
  }
}
</style>
