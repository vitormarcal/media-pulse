<template>
  <section class="artist-hero">
    <NuxtLink class="back-link" to="/music">
      Voltar para música
    </NuxtLink>

    <div class="hero-grid">
      <div class="copy">
        <p class="eyebrow">Artista</p>
        <h1>{{ title }}</h1>

        <div class="meta-list">
          <span v-for="item in heroMeta" :key="item" class="meta-pill">{{ item }}</span>
        </div>
      </div>

      <div class="cover-frame">
        <img v-if="resolvedCoverUrl" :src="resolvedCoverUrl" :alt="title">
        <div v-else class="cover-fallback">{{ title.slice(0, 1) }}</div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
const props = defineProps<{
  title: string
  coverUrl: string | null
  heroMeta: string[]
}>()

const { resolveMediaUrl } = useMediaUrl()
const resolvedCoverUrl = computed(() => resolveMediaUrl(props.coverUrl))
</script>

<style scoped>
.artist-hero {
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
  grid-template-columns: minmax(0, 1.1fr) minmax(16rem, 0.72fr);
  gap: 28px;
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

.cover-frame {
  min-height: 28rem;
  overflow: hidden;
  border-radius: 28px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.cover-frame img,
.cover-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.cover-fallback {
  display: grid;
  place-items: center;
  font-size: 5rem;
  color: var(--base-color-text-secondary);
  font-weight: 700;
}

@media (max-width: 980px) {
  .hero-grid {
    grid-template-columns: 1fr;
  }

  .cover-frame {
    min-height: 22rem;
  }
}
</style>
