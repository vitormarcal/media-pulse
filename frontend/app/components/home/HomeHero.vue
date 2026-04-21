<template>
  <section class="hero">
    <div class="hero-copy">
      <p class="eyebrow">Página inicial</p>
      <h2>{{ title }}</h2>
      <p class="intro">{{ intro }}</p>
    </div>

    <div class="hero-grid">
      <NuxtLink v-if="lead?.href" :to="lead.href" class="lead-link">
        <article class="lead-card">
          <div class="lead-image">
            <img v-if="resolvedLeadImageUrl" :src="resolvedLeadImageUrl" :alt="lead.title" />
            <div v-else class="lead-fallback">{{ lead.title.slice(0, 1) }}</div>
          </div>
          <div class="lead-body">
            <p class="lead-eyebrow">{{ lead.eyebrow }}</p>
            <h3>{{ lead.title }}</h3>
            <p class="lead-subtitle">{{ lead.subtitle }}</p>
            <p class="lead-meta">{{ lead.meta }}</p>
          </div>
        </article>
      </NuxtLink>

      <div v-else-if="lead" class="lead-link">
        <article class="lead-card">
          <div class="lead-image">
            <img v-if="resolvedLeadImageUrl" :src="resolvedLeadImageUrl" :alt="lead.title" />
            <div v-else class="lead-fallback">{{ lead.title.slice(0, 1) }}</div>
          </div>
          <div class="lead-body">
            <p class="lead-eyebrow">{{ lead.eyebrow }}</p>
            <h3>{{ lead.title }}</h3>
            <p class="lead-subtitle">{{ lead.subtitle }}</p>
            <p class="lead-meta">{{ lead.meta }}</p>
          </div>
        </article>
      </div>

      <div class="supporting-list">
        <template v-for="item in supporting" :key="item.id">
          <NuxtLink v-if="item.href" :to="item.href" class="supporting-link">
            <article class="supporting-card">
              <div class="supporting-image">
                <img v-if="resolveMediaUrl(item.imageUrl)" :src="resolveMediaUrl(item.imageUrl)" :alt="item.title" />
                <div v-else class="supporting-fallback">{{ item.title.slice(0, 1) }}</div>
              </div>
              <div class="supporting-body">
                <p>{{ item.eyebrow }}</p>
                <h3>{{ item.title }}</h3>
                <span>{{ item.meta }}</span>
              </div>
            </article>
          </NuxtLink>

          <div v-else class="supporting-link">
            <article class="supporting-card">
              <div class="supporting-image">
                <img v-if="resolveMediaUrl(item.imageUrl)" :src="resolveMediaUrl(item.imageUrl)" :alt="item.title" />
                <div v-else class="supporting-fallback">{{ item.title.slice(0, 1) }}</div>
              </div>
              <div class="supporting-body">
                <p>{{ item.eyebrow }}</p>
                <h3>{{ item.title }}</h3>
                <span>{{ item.meta }}</span>
              </div>
            </article>
          </div>
        </template>
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
.lead-link,
.supporting-link {
  display: block;
}

.hero {
  display: grid;
  gap: clamp(22px, 3vw, 34px);
  padding: clamp(26px, 4vw, 44px);
  border-radius: 40px;
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
}

.eyebrow,
.lead-eyebrow {
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
  font-size: clamp(3.1rem, 7.5vw, 5.9rem);
  line-height: 0.92;
  letter-spacing: -0.07em;
}

.intro {
  max-width: 38rem;
  margin: 14px 0 0;
  color: var(--base-color-text-secondary);
  font-size: 1.06rem;
  line-height: 1.58;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 1.3fr) minmax(18rem, 0.9fr);
  gap: 20px;
}

.lead-card {
  display: grid;
  grid-template-columns: minmax(15rem, 0.95fr) minmax(0, 1.1fr);
  gap: 22px;
  padding: 20px;
  border-radius: 32px;
  background: rgba(255, 255, 255, 0.7);
}

.lead-image {
  min-height: 24rem;
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
  margin: 8px 0 10px;
  font-size: 1.54rem;
  line-height: 0.98;
  letter-spacing: -0.03em;
}

.lead-subtitle,
.lead-meta,
.supporting-card span {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.lead-subtitle {
  font-size: 1rem;
  line-height: 1.5;
}

.lead-meta {
  margin-top: 8px;
  font-size: 0.92rem;
  color: var(--base-color-text-muted);
}

.supporting-list {
  display: grid;
  gap: 16px;
  align-content: start;
}

.supporting-card {
  display: grid;
  grid-template-columns: 104px minmax(0, 1fr);
  gap: 16px;
  align-items: center;
  padding: 16px;
  border-radius: 26px;
  background: rgba(255, 255, 255, 0.68);
  min-height: 8.5rem;
}

.supporting-image {
  aspect-ratio: 0.9;
  overflow: hidden;
  border-radius: 18px;
  border: 6px solid #fff;
  background: var(--base-color-surface-soft);
}

.supporting-image img,
.supporting-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.supporting-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-size: 2rem;
  font-weight: 700;
}

.supporting-body {
  min-width: 0;
}

.supporting-card p {
  margin: 0;
  color: var(--base-color-text-muted);
  font-size: 0.72rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

@media (max-width: 980px) {
  .hero-grid,
  .lead-card {
    grid-template-columns: 1fr;
  }

  .lead-image {
    min-height: 19rem;
  }

  .supporting-card {
    grid-template-columns: 88px minmax(0, 1fr);
  }
}
</style>
