<template>
  <section class="rail">
    <SectionHeading
      :eyebrow="eyebrow"
      :title="title"
      :description="description"
      :summary="summary"
    />

    <div class="grid">
      <MediaPosterCard
        v-for="(item, index) in items"
        :key="item.id"
        :item="item"
        :variant="cardVariant(index)"
      />
    </div>
  </section>
</template>

<script setup lang="ts">
import type { EditorialShelfItem } from '~/types/home'
import MediaPosterCard from '~/components/home/MediaPosterCard.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'

defineProps<{
  eyebrow: string
  title: string
  description: string
  summary: string
  items: EditorialShelfItem[]
}>()

function cardVariant(index: number) {
  const pattern = ['feature', 'compact', 'tall', 'standard', 'compact', 'tall'] as const
  return pattern[index % pattern.length]
}
</script>

<style scoped>
.rail {
  display: grid;
  gap: 24px;
}

.grid {
  column-count: 4;
  column-gap: 20px;
}

@media (max-width: 1280px) {
  .grid {
    column-count: 3;
  }
}

@media (max-width: 720px) {
  .grid {
    column-count: 2;
  }
}

@media (max-width: 520px) {
  .grid {
    column-count: 1;
  }
}
</style>
