<template>
  <component :is="wrapperTag" :to="href || undefined" class="strip-link">
    <article class="strip-card" :class="`variant-${variant}`">
      <div class="thumb">
        <img v-if="resolvedImageUrl" :src="resolvedImageUrl" :alt="title" loading="lazy">
        <div v-else class="thumb-fallback">{{ title.slice(0, 1) }}</div>
      </div>

      <div class="content">
        <p class="eyebrow">{{ kicker }}</p>
        <h3>{{ title }}</h3>
        <p class="subtitle">{{ subtitle }}</p>
      </div>

      <div class="aside">
        <span>{{ meta }}</span>
        <strong>{{ detail }}</strong>
      </div>
    </article>
  </component>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'

const props = defineProps<{
  kicker: string
  title: string
  subtitle: string
  meta: string
  detail: string
  imageUrl: string | null
  href?: string | null
  variant?: 'default' | 'large'
}>()

const { resolveMediaUrl } = useMediaUrl()
const resolvedImageUrl = computed(() => resolveMediaUrl(props.imageUrl))
const wrapperTag = computed(() => props.href ? NuxtLink : 'div')
</script>

<style scoped>
.strip-link {
  display: block;
}

.strip-card {
  display: grid;
  grid-template-columns: 84px minmax(0, 1fr) auto;
  gap: 18px;
  align-items: center;
  padding: 16px;
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
  margin: 5px 0 4px;
  font-size: 1.02rem;
  line-height: 1.02;
  letter-spacing: -0.02em;
}

.subtitle {
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  font-size: 0.92rem;
}

.aside {
  display: grid;
  justify-items: end;
  gap: 6px;
  font-size: 0.78rem;
  color: var(--base-color-text-muted);
}

.aside strong {
  color: var(--base-color-text-primary);
}

.variant-large {
  grid-template-columns: 132px minmax(0, 1fr) auto;
  gap: 22px;
  padding: 20px;
  border-radius: 28px;
}

.variant-large .thumb {
  width: 132px;
  border-radius: 22px;
}

.variant-large h3 {
  font-size: 1.18rem;
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
