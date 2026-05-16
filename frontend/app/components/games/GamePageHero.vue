<template>
  <section class="game-hero">
    <NuxtLink class="back-link" to="/games">Voltar para games</NuxtLink>

    <div class="hero-grid" :style="heroShellStyle">
      <div class="copy">
        <p class="eyebrow">Game</p>
        <h1>{{ title }}</h1>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
        <p v-if="description" class="description">{{ description }}</p>

        <div class="meta-list">
          <span v-for="item in heroMeta" :key="item" class="meta-pill">{{ item }}</span>
        </div>

        <div v-if="identifiers.length" class="detail-row">
          <p class="detail-label">IDs</p>
          <div class="detail-pills">
            <span v-for="identifier in identifiers" :key="identifier.id" class="detail-pill">
              {{ identifier.provider }} {{ identifier.externalId }}
            </span>
          </div>
        </div>
      </div>

      <div class="gallery">
        <div v-for="(image, index) in gallery" :key="`${image}-${index}`" class="gallery-item" :class="`slot-${index}`">
          <img v-if="resolveMediaUrl(image)" :src="resolveMediaUrl(image)" :alt="title" />
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { GamePageData } from '~/types/games'

const props = defineProps<{
  title: string
  subtitle: string | null
  description: string | null
  gallery: string[]
  heroMeta: string[]
  identifiers: GamePageData['identifiers']
}>()

const { resolveMediaUrl } = useMediaUrl()
const heroImageUrl = computed(() => props.gallery.map((image) => resolveMediaUrl(image)).find(Boolean) ?? null)
const heroShellStyle = computed(() =>
  heroImageUrl.value
    ? {
        backgroundImage: `linear-gradient(180deg, rgba(255, 255, 255, 0.86), rgba(246, 243, 238, 0.96)), url("${heroImageUrl.value}")`,
      }
    : undefined,
)
</script>

<style scoped>
.game-hero {
  display: grid;
  gap: 18px;
}

.back-link {
  width: fit-content;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.02fr) minmax(20rem, 0.98fr);
  gap: 24px;
  padding: clamp(24px, 4vw, 42px);
  border-radius: 40px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.98));
  background-size: cover;
  background-position: center;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
}

.copy {
  display: grid;
  align-content: end;
  gap: 12px;
}

.eyebrow,
.detail-label {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

.detail-label {
  color: var(--base-color-text-secondary);
}

h1 {
  margin: 0;
  font-size: clamp(3rem, 7vw, 5.8rem);
  line-height: 0.92;
  letter-spacing: 0;
}

.subtitle,
.description {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.description {
  max-width: 42rem;
  line-height: 1.6;
}

.meta-list,
.detail-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.detail-row {
  display: grid;
  gap: 8px;
}

.meta-pill,
.detail-pill {
  padding: 8px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.detail-pill {
  background: rgba(255, 255, 255, 0.82);
  color: var(--base-color-text-secondary);
}

.gallery {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  min-height: 30rem;
}

.gallery-item {
  overflow: hidden;
  border-radius: 28px;
  background: var(--base-color-surface-warm);
}

.gallery-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.slot-0 {
  grid-row: span 2;
}

@media (max-width: 900px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .gallery {
    min-height: 20rem;
  }
}
</style>
