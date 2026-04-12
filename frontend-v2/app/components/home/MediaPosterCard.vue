<template>
  <article class="poster-card" :class="`type-${item.type}`">
    <div class="poster">
      <img v-if="resolvedImageUrl" :src="resolvedImageUrl" :alt="item.title" loading="lazy">
      <div v-else class="poster-fallback">{{ item.title.slice(0, 1) }}</div>
    </div>

    <div class="body">
      <p class="kicker">{{ label }}</p>
      <h3>{{ item.title }}</h3>
      <p class="subtitle">{{ item.subtitle }}</p>
      <div class="meta-row">
        <span>{{ item.meta }}</span>
        <span>{{ item.detail }}</span>
      </div>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { EditorialShelfItem } from '~/types/home'

const props = defineProps<{
  item: EditorialShelfItem
}>()

const { resolveMediaUrl } = useMediaUrl()
const resolvedImageUrl = computed(() => resolveMediaUrl(props.item.imageUrl))

const label = computed(() => {
  switch (props.item.type) {
    case 'music':
      return 'Album'
    case 'show':
      return 'Série'
    case 'movie':
      return 'Filme'
    case 'book':
      return 'Livro'
  }
})
</script>

<style scoped>
.poster-card {
  display: grid;
  gap: 14px;
}

.poster {
  aspect-ratio: 0.82;
  overflow: hidden;
  border-radius: 24px;
  background:
    linear-gradient(160deg, rgba(230, 0, 35, 0.12), rgba(33, 25, 34, 0.06)),
    var(--base-color-surface-soft);
  border: 8px solid #fff;
}

img,
.poster-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.poster-fallback {
  display: grid;
  place-items: center;
  font-size: 3rem;
  font-weight: 700;
  color: var(--base-color-text-secondary);
}

.body {
  display: grid;
  gap: 6px;
}

.kicker {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h3 {
  margin: 0;
  font-size: 1.1rem;
  line-height: 1.05;
}

.subtitle,
.meta-row {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.meta-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 0.8rem;
}
</style>
