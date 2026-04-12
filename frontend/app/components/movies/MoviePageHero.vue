<template>
  <section class="movie-hero">
    <NuxtLink class="back-link" to="/">
      Voltar para a capa
    </NuxtLink>

    <div class="hero-grid">
      <div class="copy">
        <p class="eyebrow">Filme</p>
        <h1>{{ title }}</h1>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
        <p v-if="description" class="description">{{ description }}</p>

        <div class="meta-list">
          <span v-for="item in heroMeta" :key="item" class="meta-pill">{{ item }}</span>
        </div>
      </div>

      <div class="gallery">
        <div v-for="(image, index) in gallery" :key="`${image}-${index}`" class="gallery-item" :class="`slot-${index}`">
          <img v-if="resolveMediaUrl(image)" :src="resolveMediaUrl(image)" :alt="title">
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
defineProps<{
  title: string
  subtitle: string | null
  description: string | null
  gallery: string[]
  heroMeta: string[]
}>()

const { resolveMediaUrl } = useMediaUrl()
</script>

<style scoped>
.movie-hero {
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
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
}

.copy {
  display: grid;
  align-content: end;
  gap: 12px;
}

.eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

h1 {
  margin: 0;
  font-size: clamp(3rem, 7vw, 5.8rem);
  line-height: 0.92;
  letter-spacing: -0.07em;
}

.subtitle {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 1.02rem;
}

.description {
  max-width: 42rem;
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}

.meta-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.meta-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.gallery {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 14px;
  min-height: 30rem;
}

.gallery-item {
  overflow: hidden;
  border-radius: 24px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.gallery-item img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.slot-0 {
  grid-column: span 2;
  min-height: 19rem;
}

.slot-1,
.slot-2,
.slot-3 {
  min-height: 10rem;
}

@media (max-width: 980px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .gallery {
    min-height: unset;
  }
}
</style>
