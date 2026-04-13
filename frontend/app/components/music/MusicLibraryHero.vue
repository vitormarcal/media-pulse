<template>
  <section class="library-hero">
    <div class="copy">
      <NuxtLink class="back-link" :to="backLink">
        {{ backLabel }}
      </NuxtLink>

      <p class="eyebrow">Biblioteca de música</p>
      <h1>{{ title }}</h1>
      <p class="intro">{{ intro }}</p>

      <div class="actions">
        <NuxtLink class="action-accent" :to="accentLink">
          {{ accentLabel }}
        </NuxtLink>
      </div>
    </div>

    <component :is="spotlightWrapper" :to="spotlight?.href || undefined" class="spotlight-link">
      <article class="spotlight-card">
        <div class="spotlight-poster">
          <img v-if="spotlightImageUrl" :src="spotlightImageUrl" :alt="spotlight?.title || title">
          <div v-else class="spotlight-fallback">{{ (spotlight?.title || 'M').slice(0, 1) }}</div>
        </div>

        <div class="spotlight-body">
          <p class="spotlight-kicker">Entrada de arquivo</p>
          <h2>{{ spotlight?.title || title }}</h2>
          <p v-if="spotlight" class="spotlight-subtitle">{{ spotlight.subtitle }}</p>
          <p v-if="spotlight" class="spotlight-meta">{{ spotlight.meta }}</p>
          <p v-if="spotlight" class="spotlight-note">{{ spotlight.note }}</p>
        </div>
      </article>
    </component>
  </section>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'

const props = defineProps<{
  title: string
  intro: string
  backLink: string
  backLabel: string
  accentLink: string
  accentLabel: string
  spotlight: {
    title: string
    subtitle: string
    imageUrl: string | null
    href: string | null
    meta: string
    note: string
  } | null
}>()

const { resolveMediaUrl } = useMediaUrl()
const spotlightImageUrl = computed(() => props.spotlight ? resolveMediaUrl(props.spotlight.imageUrl) : null)
const spotlightWrapper = computed(() => props.spotlight?.href ? NuxtLink : 'div')
</script>

<style scoped>
.library-hero {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(0, 0.9fr) minmax(20rem, 1.1fr);
  align-items: end;
}

.copy {
  display: grid;
  gap: 12px;
  align-content: end;
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
  letter-spacing: -0.075em;
}

.intro {
  max-width: 42rem;
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.62;
}

.back-link {
  width: fit-content;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 4px;
}

.action-accent {
  padding: 8px 14px;
  border-radius: 16px;
  font-size: 0.8rem;
  background: var(--base-color-brand-red);
  color: #000;
}

.spotlight-link {
  display: block;
}

.spotlight-card {
  display: grid;
  grid-template-columns: minmax(13rem, 0.92fr) minmax(0, 1fr);
  gap: 20px;
  padding: clamp(18px, 3vw, 28px);
  border-radius: 40px;
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 52%, white);
}

.spotlight-poster {
  aspect-ratio: 0.84;
  overflow: hidden;
  border-radius: 28px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.spotlight-poster img,
.spotlight-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.spotlight-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-weight: 700;
  font-size: 3rem;
}

.spotlight-body {
  display: grid;
  align-content: end;
  gap: 8px;
}

.spotlight-kicker {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

h2,
.spotlight-subtitle,
.spotlight-meta,
.spotlight-note {
  margin: 0;
}

h2 {
  font-size: clamp(1.9rem, 3.4vw, 3rem);
  line-height: 0.98;
  letter-spacing: -0.04em;
}

.spotlight-subtitle,
.spotlight-meta {
  color: var(--base-color-text-secondary);
}

.spotlight-note {
  color: var(--base-color-text-muted);
  font-size: 0.88rem;
}

@media (max-width: 900px) {
  .library-hero,
  .spotlight-card {
    grid-template-columns: 1fr;
  }
}
</style>
