<template>
  <section class="in-progress">
    <SectionHeading
      eyebrow="Em curso"
      title="O que ainda está aberto"
      description="O que ficou pela metade porque ainda merece retorno."
      summary="Séries em andamento e leituras correntes, lado a lado, para lembrar onde vale retomar."
    />

    <div class="grid">
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
.in-progress,
.grid {
  display: grid;
  gap: 20px;
}

.grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
  align-items: start;
}

@media (max-width: 820px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
