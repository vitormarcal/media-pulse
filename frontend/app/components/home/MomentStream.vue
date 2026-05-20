<template>
  <section class="moment-stream">
    <SectionHeading eyebrow="Recente" title="Últimos sinais" />

    <div class="list">
      <MediaStripCard v-for="item in items" :key="item.id" :item="item" variant="large" />
    </div>
  </section>
</template>

<script setup lang="ts">
import type { EditorialShelfItem } from '~/types/home'
import MediaStripCard from '~/components/home/MediaStripCard.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'

defineProps<{
  items: EditorialShelfItem[]
}>()
</script>

<style scoped>
.moment-stream,
.list {
  display: grid;
  gap: 20px;
}

.list {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (max-width: 900px) {
  .list {
    grid-template-columns: 1fr;
  }
}
</style>
