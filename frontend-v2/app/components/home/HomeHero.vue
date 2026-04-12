<template>
  <section class="hero">
    <div class="hero-copy">
      <p class="eyebrow">Página inicial</p>
      <h2>{{ title }}</h2>
      <p class="intro">{{ intro }}</p>
    </div>

    <div class="hero-grid">
      <article v-if="lead" class="lead-card">
        <div class="lead-image">
          <img v-if="resolvedLeadImageUrl" :src="resolvedLeadImageUrl" :alt="lead.title">
          <div v-else class="lead-fallback">{{ lead.title.slice(0, 1) }}</div>
        </div>
        <div class="lead-body">
          <p class="lead-eyebrow">{{ lead.eyebrow }}</p>
          <h3>{{ lead.title }}</h3>
          <p class="lead-subtitle">{{ lead.subtitle }}</p>
          <p class="lead-meta">{{ lead.meta }}</p>
        </div>
      </article>

      <div class="supporting-list">
        <article v-for="item in supporting" :key="item.id" class="supporting-card">
          <p>{{ item.eyebrow }}</p>
          <h3>{{ item.title }}</h3>
          <span>{{ item.meta }}</span>
        </article>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { EditorialHighlight } from '~/types/home'

const props = defineProps<{
  title: string
  intro: string
  lead: EditorialHighlight | null
  supporting: EditorialHighlight[]
}>()

const { resolveMediaUrl } = useMediaUrl()
const resolvedLeadImageUrl = computed(() => resolveMediaUrl(props.lead?.imageUrl))
</script>

<style scoped>
.hero {
  display: grid;
  gap: 28px;
  padding: clamp(24px, 4vw, 40px);
  border-radius: 40px;
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(246, 243, 238, 0.95));
  box-shadow: var(--base-shadow-soft);
}

.eyebrow,
.lead-eyebrow,
.supporting-card p {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.78rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

h2 {
  max-width: 12ch;
  margin: 0;
  font-size: clamp(2.6rem, 7vw, 5.6rem);
  line-height: 0.95;
  letter-spacing: -0.06em;
}

.intro {
  max-width: 42rem;
  margin: 12px 0 0;
  color: var(--base-color-text-secondary);
  font-size: 1.05rem;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(18rem, 0.9fr);
  gap: 18px;
}

.lead-card {
  display: grid;
  grid-template-columns: minmax(15rem, 0.95fr) minmax(0, 1.1fr);
  gap: 20px;
  padding: 18px;
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.72);
}

.lead-image {
  min-height: 22rem;
  overflow: hidden;
  border-radius: 28px;
  background: var(--base-color-surface-soft);
  border: 8px solid #fff;
}

img,
.lead-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.lead-fallback {
  display: grid;
  place-items: center;
  font-size: 5rem;
  color: var(--base-color-text-secondary);
}

.lead-body {
  align-self: end;
}

.lead-body h3,
.supporting-card h3 {
  margin: 8px 0;
  font-size: 1.5rem;
  line-height: 1;
}

.lead-subtitle,
.lead-meta,
.supporting-card span {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.supporting-list {
  display: grid;
  gap: 14px;
}

.supporting-card {
  display: grid;
  gap: 8px;
  align-content: end;
  padding: 18px;
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.78);
  min-height: 10rem;
}

@media (max-width: 980px) {
  .hero-grid,
  .lead-card {
    grid-template-columns: 1fr;
  }

  .lead-image {
    min-height: 18rem;
  }
}
</style>
