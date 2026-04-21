<template>
  <component :is="wrapperTag" :to="item.href" class="library-link">
    <article class="library-card" :class="{ dormant: item.isDormant }">
      <div class="poster">
        <img v-if="resolvedImageUrl" :src="resolvedImageUrl" :alt="item.title" loading="lazy" />
        <div v-else class="poster-fallback">{{ item.title.slice(0, 1) }}</div>
      </div>

      <div class="body">
        <div class="title-row">
          <div>
            <h3>{{ item.title }}</h3>
            <p class="subtitle">{{ item.subtitle }}</p>
          </div>
          <span class="aside">{{ item.aside }}</span>
        </div>

        <div class="meta-row">
          <p class="progress-label">{{ item.progressLabel }}</p>
          <p class="activity">{{ item.activityLabel }}</p>
        </div>
      </div>
    </article>
  </component>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import type { BookLibraryCardModel } from '~/types/books'

const props = defineProps<{
  item: BookLibraryCardModel
}>()

const { resolveMediaUrl } = useMediaUrl()
const resolvedImageUrl = computed(() => resolveMediaUrl(props.item.imageUrl))
const wrapperTag = computed(() => (props.item.href ? NuxtLink : 'div'))
</script>

<style scoped>
.library-link {
  display: block;
}

.library-card {
  display: grid;
  gap: 14px;
  break-inside: avoid;
  margin-bottom: 20px;
}

.poster {
  aspect-ratio: 0.72;
  overflow: hidden;
  border-radius: 24px;
  border: 8px solid #fff;
  background: linear-gradient(160deg, rgba(230, 0, 35, 0.1), rgba(33, 25, 34, 0.05)), var(--base-color-surface-soft);
}

.poster img,
.poster-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.poster-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-weight: 700;
  font-size: 3rem;
}

.body {
  display: grid;
  gap: 10px;
}

.title-row {
  display: flex;
  gap: 12px;
  justify-content: space-between;
  align-items: start;
}

h3,
.subtitle,
.progress-label,
.activity {
  margin: 0;
}

h3 {
  font-size: 1.18rem;
  line-height: 1.02;
  letter-spacing: -0.04em;
}

.subtitle,
.progress-label {
  color: var(--base-color-text-secondary);
}

.subtitle {
  margin-top: 4px;
}

.aside {
  padding: 6px 10px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 82%, white);
  font-size: 0.76rem;
}

.meta-row {
  display: grid;
  gap: 4px;
  padding-top: 2px;
}

.progress-label,
.activity {
  margin: 0;
  font-size: 0.82rem;
}

.progress-label {
  color: var(--base-color-text-primary);
  font-weight: 700;
}

.activity {
  color: var(--base-color-text-muted);
}

.library-card.dormant .aside {
  background: color-mix(in srgb, var(--base-color-surface-warm) 88%, white);
}
</style>
