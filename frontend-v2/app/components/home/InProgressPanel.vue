<template>
  <section class="in-progress">
    <SectionHeading
      eyebrow="Em curso"
      title="O que ainda está aberto"
      description="Séries em andamento e leituras correntes compartilham o mesmo espaço para reforçar continuidade."
      summary="O foco aqui não é contagem e sim permanência: o que continua pedindo retorno."
    />

    <div class="grid">
      <MediaStripCard v-for="item in items" :key="item.id" :item="item" />
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
  gap: 18px;
}

.grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

@media (max-width: 820px) {
  .grid {
    grid-template-columns: 1fr;
  }
}
</style>
