<template>
  <div class="viewer-frame" :aria-busy="Boolean(src && !ready && !failed)">
    <img
      v-if="src && !failed"
      :src="src"
      :alt="alt"
      :class="{ ready }"
      :aria-hidden="!ready"
      referrerpolicy="no-referrer"
      @load="ready = true"
      @error="failed = true"
    />
    <p v-if="!src || failed" class="viewer-feedback" role="status">Imagem indisponível</p>
    <p v-else-if="!ready" class="viewer-feedback" role="status">
      <span class="loading-ring" aria-hidden="true" />Carregando imagem…
    </p>
  </div>
</template>
<script setup lang="ts">
defineProps<{ src: string | null; alt: string }>()
const ready = ref(false)
const failed = ref(false)
</script>
<style scoped>
.viewer-frame {
  position: relative;
  width: 100%;
  height: 100%;
  min-width: 0;
  min-height: 0;
  display: grid;
  place-items: center;
}
img {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: contain;
  opacity: 0;
}
img.ready {
  opacity: 1;
}
.viewer-feedback {
  display: flex;
  align-items: center;
  gap: 10px;
  margin: 0;
  color: #e5e5e0;
  font-size: 0.88rem;
}
.loading-ring {
  width: 18px;
  height: 18px;
  border: 2px solid currentColor;
  border-right-color: transparent;
  border-radius: 50%;
  animation: loading 1s linear infinite;
}
@keyframes loading {
  to {
    transform: rotate(360deg);
  }
}
@media (prefers-reduced-motion: reduce) {
  .loading-ring {
    animation: none;
  }
}
</style>
