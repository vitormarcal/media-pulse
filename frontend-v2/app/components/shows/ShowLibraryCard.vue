<template>
  <component :is="wrapperTag" :to="item.href || undefined" class="library-link">
    <article class="library-card" :class="{ dormant: item.isDormant }">
      <div class="poster">
        <img v-if="resolvedImageUrl" :src="resolvedImageUrl" :alt="item.title" loading="lazy">
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

        <p class="progress-label">{{ item.progressLabel }}</p>
        <div class="progress-bar">
          <span class="progress-fill" :style="{ width: `${item.progressValue}%` }" />
        </div>
        <p class="activity">{{ item.activityLabel }}</p>
      </div>
    </article>
  </component>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import type { ShowLibraryCardModel } from '~/types/shows'

const props = defineProps<{
  item: ShowLibraryCardModel
}>()

const { resolveMediaUrl } = useMediaUrl()
const resolvedImageUrl = computed(() => resolveMediaUrl(props.item.imageUrl))
const wrapperTag = computed(() => props.item.href ? NuxtLink : 'div')
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
  aspect-ratio: 0.82;
  overflow: hidden;
  border-radius: 24px;
  border: 8px solid #fff;
  background:
    linear-gradient(160deg, rgba(230, 0, 35, 0.1), rgba(33, 25, 34, 0.05)),
    var(--base-color-surface-soft);
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
  gap: 8px;
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
  font-size: 1.12rem;
  line-height: 1.02;
  letter-spacing: -0.03em;
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
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 78%, white);
  font-size: 0.76rem;
}

.progress-label,
.activity {
  font-size: 0.82rem;
}

.activity {
  color: var(--base-color-text-muted);
}

.progress-bar {
  height: 10px;
  overflow: hidden;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 74%, white);
}

.progress-fill {
  display: block;
  height: 100%;
  border-radius: inherit;
  background: linear-gradient(90deg, #e60023, #f15f6e);
}

.library-card.dormant .progress-fill {
  background: linear-gradient(90deg, #c8c8c1, #b9b8af);
}
</style>
