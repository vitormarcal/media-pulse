<template>
  <img v-if="src && failedSrc !== src" :src="src" :alt="alt" @error="failedSrc = src" />
  <div v-else :class="fallbackClass" role="img" :aria-label="src ? 'Imagem indisponível' : fallback">
    {{ fallback }}
  </div>
</template>
<script setup lang="ts">
withDefaults(defineProps<{ src?: string | null; alt: string; fallback?: string; fallbackClass?: string }>(), {
  src: null,
  fallback: 'Sem imagem',
  fallbackClass: 'media-image-fallback',
})
const failedSrc = ref<string | null>(null)
</script>
<style scoped>
.media-image-fallback {
  display: grid;
  place-items: center;
  min-height: 4rem;
  padding: 16px;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-secondary);
  border-radius: 16px;
  text-align: center;
}
</style>
