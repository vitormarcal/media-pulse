<template>
  <section class="moment-stream">
    <SectionHeading
      eyebrow="Agora e há pouco"
      title="O fluxo mais recente, misturado por mídia"
      description="Sem separar por produto logo de cara: primeiro vem a sensação de movimento."
      summary="A mistura entre formatos evita a sensação de dashboard e aproxima a home de uma capa editorial."
    />

    <div class="list">
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
.moment-stream,
.list {
  display: grid;
  gap: 18px;
}
</style>
