<template>
  <article class="strip-card" :class="`variant-${variant}`">
    <div class="thumb">
      <img v-if="resolvedImageUrl" :src="resolvedImageUrl" :alt="item.title" loading="lazy">
      <div v-else class="thumb-fallback">{{ item.title.slice(0, 1) }}</div>
    </div>

    <div class="content">
      <p class="eyebrow">{{ label }}</p>
      <h3>{{ item.title }}</h3>
      <p class="subtitle">{{ item.subtitle }}</p>
    </div>

    <div class="aside">
      <span>{{ item.meta }}</span>
      <strong>{{ item.detail }}</strong>
    </div>
  </article>
</template>

<script setup lang="ts">
import type { EditorialShelfItem } from '~/types/home'

const props = defineProps<{
  item: EditorialShelfItem
  variant?: 'default' | 'large'
}>()

const { resolveMediaUrl } = useMediaUrl()
const resolvedImageUrl = computed(() => resolveMediaUrl(props.item.imageUrl))

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
.strip-card {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) auto;
  gap: 16px;
  align-items: center;
  padding: 14px;
  border-radius: 24px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 82%, var(--base-color-surface-soft));
}

.thumb {
  width: 84px;
  aspect-ratio: 0.88;
  overflow: hidden;
  border-radius: 18px;
  background: var(--base-color-surface-soft);
  border: 6px solid #fff;
}

img,
.thumb-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.thumb-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-weight: 700;
}

.content {
  min-width: 0;
}

.eyebrow,
.subtitle,
.aside {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.eyebrow {
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h3 {
  margin: 4px 0;
  font-size: 1rem;
}

.subtitle {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}

.aside {
  display: grid;
  justify-items: end;
  gap: 6px;
  font-size: 0.8rem;
}

.aside strong {
  color: var(--base-color-text-primary);
}

.variant-large {
  grid-template-columns: 132px minmax(0, 1fr) auto;
  gap: 20px;
  padding: 18px;
  border-radius: 28px;
}

.variant-large .thumb {
  width: 132px;
  border-radius: 22px;
}

.variant-large h3 {
  font-size: 1.14rem;
}

.variant-large .subtitle,
.variant-large .aside {
  font-size: 0.9rem;
}

@media (max-width: 720px) {
  .strip-card {
    grid-template-columns: 72px 1fr;
  }

  .variant-large {
    grid-template-columns: 96px 1fr;
  }

  .variant-large .thumb {
    width: 96px;
  }

  .aside {
    grid-column: 2;
    justify-items: start;
  }
}
</style>
