<template>
  <section class="shows-collection-hero" :style="heroShellStyle">
    <div class="top-actions">
      <NuxtLink class="back-link" :to="backLink"> {{ backLabel }} </NuxtLink>

      <slot name="actions">
        <NuxtLink v-if="!accentOwnerOnly || owner.authenticated" class="library-link" :to="accentLink">
          {{ accentLabel }}
        </NuxtLink>
      </slot>
    </div>

    <div class="hero-grid">
      <div class="copy">
        <p class="eyebrow">Séries</p>
        <h1>{{ title }}</h1>
        <p v-if="intro" class="intro">{{ intro }}</p>
      </div>

      <component :is="leadWrapper" :to="lead?.href || undefined" class="lead-link">
        <article class="lead-card">
          <div class="lead-poster">
            <MediaImage
              :src="leadImageUrl"
              :alt="lead?.title || title"
              fallback-class="lead-fallback"
              :fallback="(lead?.title || 'S').slice(0, 1)"
            />
          </div>

          <div class="lead-body">
            <p class="lead-kicker">{{ lead?.eyebrow || 'Série' }}</p>
            <h2>{{ lead?.title || leadPlaceholder || 'Sem série em destaque agora' }}</h2>
            <p v-if="lead?.subtitle" class="lead-subtitle">{{ lead.subtitle }}</p>
            <p v-if="lead" class="lead-meta">{{ lead.meta }}</p>
          </div>
        </article>
      </component>
    </div>

    <div
      v-if="supporting.length"
      class="supporting-list"
      :class="{ 'browse-rail': browse }"
      :tabindex="browse ? 0 : undefined"
      aria-label="Outras séries recentes"
    >
      <component
        :is="item.href ? NuxtLink : 'div'"
        v-for="item in supporting"
        :key="item.id"
        :to="item.href || undefined"
        class="supporting-link"
      >
        <article class="supporting-card">
          <div class="supporting-thumb">
            <MediaImage
              :src="resolveMediaUrl(item.imageUrl)"
              :alt="item.title"
              loading="lazy"
              fallback-class="supporting-fallback"
              :fallback="item.title.slice(0, 1)"
            />
          </div>
          <div class="supporting-body">
            <p class="supporting-kicker">{{ item.eyebrow }}</p>
            <h3>{{ item.title }}</h3>
            <p>{{ item.meta }}</p>
          </div>
        </article>
      </component>
    </div>
    <div v-if="$slots.search" class="hero-search"><slot name="search" /></div>
  </section>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import type { EditorialHighlight } from '~/types/home'

const owner = useOwnerAuth()

const props = withDefaults(
  defineProps<{
    title: string
    intro: string
    lead: EditorialHighlight | null
    supporting: EditorialHighlight[]
    backLink?: string
    backLabel?: string
    accentLink?: string
    accentLabel?: string
    accentOwnerOnly?: boolean
    leadPlaceholder?: string
    browse?: boolean
  }>(),
  {
    leadPlaceholder: '',
    backLink: '/',
    backLabel: 'Voltar para a capa',
    accentLink: '/shows?add=1',
    accentLabel: 'Adicionar série',
  },
)

const { resolveMediaUrl } = useMediaUrl()

const leadImageUrl = computed(() => (props.lead ? resolveMediaUrl(props.lead.imageUrl) : null))
const leadWrapper = computed(() => (props.lead?.href ? NuxtLink : 'div'))
const heroShellStyle = computed(() =>
  leadImageUrl.value
    ? {
        backgroundImage: `linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(246, 243, 238, 0.97)), radial-gradient(circle at top right, rgba(230, 0, 35, 0.1), transparent 28%), url("${leadImageUrl.value}")`,
      }
    : undefined,
)
</script>

<style scoped>
.shows-collection-hero {
  display: grid;
  gap: 18px;
  padding: clamp(24px, 4vw, 36px);
  border-radius: 40px;
  background-image:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.98));
  background-size: cover;
  background-position: center;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
}

.top-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.back-link {
  width: fit-content;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.library-link {
  width: fit-content;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-brand-red);
  color: #000;
  font-size: 0.8rem;
}

.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(20rem, 1.1fr);
  gap: 24px;
  align-items: end;
}

.copy {
  display: grid;
  gap: 12px;
  align-content: end;
}

.eyebrow,
.lead-kicker,
.supporting-kicker {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

h1 {
  margin: 0;
  font-size: clamp(3.2rem, 7vw, 6rem);
  line-height: 0.92;
  letter-spacing: -0.075em;
}

.intro {
  max-width: 34rem;
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.62;
}

.lead-link {
  display: block;
}

.lead-card {
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

.lead-poster {
  aspect-ratio: 0.76;
  overflow: hidden;
  border-radius: 28px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.lead-poster :deep(img),
.lead-fallback,
.supporting-thumb :deep(img),
.supporting-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.lead-fallback,
.supporting-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-weight: 700;
}

.lead-body {
  display: grid;
  align-content: end;
  gap: 8px;
}

h2,
h3 {
  margin: 0;
  line-height: 0.98;
  letter-spacing: -0.04em;
}

h2 {
  font-size: clamp(1.9rem, 3.4vw, 3rem);
}

.lead-subtitle,
.lead-meta,
.supporting-body p {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.lead-meta {
  font-size: 0.9rem;
}

.supporting-list {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 18px;
}

.supporting-link {
  display: block;
}

.supporting-card {
  display: grid;
  gap: 12px;
}

.supporting-thumb {
  aspect-ratio: 0.92;
  overflow: hidden;
  border-radius: 24px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.supporting-body {
  display: grid;
  gap: 5px;
}

h3 {
  font-size: 1.08rem;
}

.supporting-body p {
  font-size: 0.86rem;
}

@media (max-width: 980px) {
  .hero-grid,
  .lead-card,
  .supporting-list {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  h1 {
    font-size: clamp(2.4rem, 14vw, 4.2rem);
  }
}
.hero-search {
  display: grid;
  gap: 12px;
  padding-top: 18px;
  border-top: 1px solid var(--base-color-border);
}
.lead-poster :deep(.lead-fallback),
.supporting-thumb :deep(.supporting-fallback) {
  width: 100%;
  height: 100%;
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-weight: 700;
}
.supporting-list:focus-visible {
  outline: 2px solid var(--base-color-focus);
  outline-offset: 4px;
}
.supporting-body,
.lead-body,
.copy {
  min-width: 0;
  overflow-wrap: anywhere;
}
@media (max-width: 980px) {
  .supporting-list.browse-rail {
    grid-template-columns: none;
    grid-auto-flow: column;
    grid-auto-columns: clamp(170px, 42vw, 250px);
    overflow-x: auto;
    overscroll-behavior-x: contain;
    scroll-snap-type: x proximity;
  }
  .browse-rail .supporting-link {
    scroll-snap-align: start;
  }
}
</style>
