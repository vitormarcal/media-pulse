<template>
  <section class="rail">
    <SectionHeading
      :eyebrow="eyebrow"
      :title="title"
      :description="description"
      :summary="summary"
    />

    <div class="grid">
      <MediaPosterCard v-for="item in items" :key="item.id" :item="item" />
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
</script>

<style scoped>
.rail {
  display: grid;
  gap: 22px;
}

.grid {
  display: grid;
  grid-template-columns: repeat(6, minmax(0, 1fr));
  gap: 18px;
}

@media (max-width: 1280px) {
  .grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}

@media (max-width: 720px) {
  .grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 520px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
