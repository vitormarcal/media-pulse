<template>
  <component :is="wrapperTag" :to="item.href || undefined" class="poster-link">
    <article class="poster-card" :class="[`type-${item.type}`, `variant-${variant}`]">
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
  </component>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import type { EditorialShelfItem } from '~/types/home'

const props = defineProps<{
  item: EditorialShelfItem
  variant?: 'feature' | 'tall' | 'standard' | 'compact'
}>()

const { resolveMediaUrl } = useMediaUrl()
const resolvedImageUrl = computed(() => resolveMediaUrl(props.item.imageUrl))
const wrapperTag = computed(() => props.item.href ? NuxtLink : 'div')

const label = computed(() => {
  switch (props.item.type) {
    case 'music':
      return 'Álbum'
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
.poster-link {
  display: block;
}

.poster-card {
  display: grid;
  gap: 13px;
  break-inside: avoid;
  margin-bottom: 20px;
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
  gap: 5px;
}

.kicker {
  margin: 0;
  color: var(--base-color-text-muted);
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h3 {
  margin: 0;
  font-size: 1.08rem;
  line-height: 1.02;
  letter-spacing: -0.025em;
}

.subtitle,
.meta-row {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.subtitle {
  line-height: 1.5;
}

.meta-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  font-size: 0.78rem;
  color: var(--base-color-text-muted);
}

.variant-feature .poster {
  aspect-ratio: 0.66;
}

.variant-feature h3 {
  font-size: 1.34rem;
}

.variant-tall .poster {
  aspect-ratio: 0.74;
}

.variant-standard .poster {
  aspect-ratio: 0.86;
}

.variant-compact .poster {
  aspect-ratio: 1.04;
}
</style>
