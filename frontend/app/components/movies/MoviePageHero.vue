<template>
  <section class="movie-hero">
    <img
      v-if="leadImage && !leadFailed"
      class="hero-backdrop"
      :src="resolveMediaUrl(leadImage) || undefined"
      alt=""
      aria-hidden="true"
      @error="leadFailed = true"
    />
    <nav
      class="hero-topbar"
      :aria-label="
        mediaType === 'seasons'
          ? 'Navegação da temporada'
          : mediaType === 'shows'
            ? 'Navegação da série'
            : 'Navegação do filme'
      "
    >
      <NuxtLink class="back-link" :to="backTo">{{ backLabel }}</NuxtLink>
    </nav>

    <div class="hero-grid" :class="{ 'without-images': !leadImage || leadFailed }">
      <div class="copy">
        <h1 ref="heading" :class="{ 'long-title': compactTitle }" tabindex="-1">{{ title }}</h1>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
        <slot name="context" />
      </div>
      <button
        v-if="leadImage && !leadFailed"
        type="button"
        class="lead-image image-button"
        :aria-label="`Ampliar capa de ${title}`"
        @click="$emit('viewImage', 0)"
      >
        <img :src="resolveMediaUrl(leadImage) || undefined" :alt="`Capa de ${title}`" @error="leadFailed = true" />
      </button>
      <div v-if="description" class="synopsis">
        <p class="description">{{ descriptionExpanded ? description : descriptionPreview }}</p>
        <button
          v-if="description.length > 320"
          type="button"
          class="synopsis-toggle"
          :aria-expanded="descriptionExpanded"
          @click="descriptionExpanded = !descriptionExpanded"
        >
          {{ descriptionExpanded ? 'Ler menos' : 'Ler mais' }}
        </button>
      </div>
    </div>

    <ul
      v-if="supportingImages.length"
      class="supporting-images"
      :class="{ 'few-images': supportingImages.length <= 3 }"
      :aria-label="
        mediaType === 'seasons'
          ? 'Outras imagens da temporada'
          : mediaType === 'shows'
            ? 'Outras imagens da série'
            : 'Outras imagens do filme'
      "
      tabindex="0"
    >
      <li v-for="(image, index) in supportingImages" :key="`${image}-${index}`">
        <button
          type="button"
          class="supporting-image image-button"
          :aria-label="`Ampliar imagem ${index + 2} de ${title}`"
          @click="$emit('viewImage', index + 1)"
        >
          <MediaImage :src="resolveMediaUrl(image)" :alt="`Imagem ${index + 2} de ${title}`" loading="lazy" />
        </button>
      </li>
    </ul>
    <div v-if="gallery.length || $slots['image-actions']" class="gallery-actions">
      <button v-if="gallery.length" ref="viewAllButton" type="button" class="view-all" @click="$emit('viewImage', 0)">
        {{ imageCount === 1 ? 'Ver imagem' : `Ver todas as ${imageCount} imagens` }}
      </button>
      <slot name="image-actions" />
    </div>
    <div v-if="$slots.personal" class="personal-slot"><slot name="personal" /></div>
  </section>
</template>

<script setup lang="ts">
const props = withDefaults(
  defineProps<{
    title: string
    subtitle: string | null
    description: string | null
    gallery: string[]
    imageCount: number
    mediaType?: 'movies' | 'shows' | 'seasons'
    backTo?: string
    backLabel?: string
  }>(),
  { backTo: '/movies', backLabel: 'Voltar para filmes', mediaType: 'movies' },
)

defineEmits<{ viewImage: [index: number] }>()
const viewAllButton = ref<HTMLButtonElement>()
const heading = ref<HTMLHeadingElement>()
defineExpose({ focusGallery: () => (viewAllButton.value || heading.value)?.focus() })
const { resolveMediaUrl } = useMediaUrl()
const compactTitle = computed(
  () =>
    props.title.length > 35 ||
    (props.mediaType === 'seasons' && props.title.split(/\s+/).some((word) => word.length > 12)),
)
const descriptionExpanded = ref(false)
const leadFailed = ref(false)
const leadImage = computed(() => props.gallery[0])
const supportingImages = computed(() => props.gallery.slice(1))
watch(leadImage, () => {
  leadFailed.value = false
})
watch(
  () => props.title,
  () => {
    descriptionExpanded.value = false
  },
)
const descriptionPreview = computed(() => {
  const description = props.description || ''
  return description.length > 320 ? `${description.slice(0, 320).replace(/\s+\S*$/, '')}…` : description
})
</script>

<style scoped>
.movie-hero {
  display: grid;
  gap: 24px;
  position: relative;
  isolation: isolate;
  overflow: hidden;
  padding: clamp(20px, 3vw, 36px);
  border-radius: 40px;
  background: var(--base-color-surface-soft);
}
.hero-backdrop {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.06;
  filter: blur(18px);
  transform: scale(1.06);
  pointer-events: none;
}
.movie-hero::after {
  content: '';
  position: absolute;
  inset: 0;
  background: linear-gradient(90deg, rgba(250, 249, 246, 0.72), rgba(250, 249, 246, 0.12));
  pointer-events: none;
}
.movie-hero > :not(.hero-backdrop) {
  position: relative;
  z-index: 1;
}
.hero-topbar,
.gallery-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 10px;
}
.back-link,
.view-all {
  width: fit-content;
  min-height: 44px;
  padding: 10px 16px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.88rem;
}
.back-link {
  display: inline-flex;
  align-items: center;
}
.view-all {
  cursor: pointer;
  font-weight: 700;
}
.hero-grid {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(0, 1.1fr);
  grid-template-areas: 'copy lead' 'synopsis lead';
  grid-template-rows: auto 1fr;
  align-items: start;
  gap: 20px 32px;
}
.hero-grid.without-images {
  grid-template-columns: minmax(0, 1fr);
  grid-template-areas: 'copy' 'synopsis';
}
.copy {
  grid-area: copy;
  min-width: 0;
  display: grid;
  gap: 16px;
}
h1 {
  margin: 0;
  font-size: clamp(2.6rem, 5.8vw, 5.4rem);
  overflow-wrap: anywhere;
  line-height: 0.98;
  letter-spacing: -0.06em;
}
h1.long-title {
  font-size: clamp(2.4rem, 4vw, 3.6rem);
  line-height: 1.04;
  letter-spacing: -0.045em;
}
.subtitle {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 1.02rem;
}
.synopsis {
  grid-area: synopsis;
  min-width: 0;
  display: grid;
  gap: 8px;
}
.description {
  max-width: 42rem;
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}
.synopsis-toggle {
  justify-self: start;
  border: 0;
  padding: 10px 0;
  background: transparent;
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.88rem;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}
.image-button {
  display: block;
  min-width: 0;
  max-width: 100%;
  padding: 0;
  border: 0;
  border-radius: 28px;
  background: transparent;
  cursor: zoom-in;
}
.lead-image {
  grid-area: lead;
  width: fit-content;
  justify-self: center;
}
.lead-image img {
  display: block;
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 560px;
  border-radius: 28px;
}
.supporting-images {
  --supporting-height: 180px;
  display: flex;
  align-items: start;
  overflow-x: auto;
  gap: 20px;
  list-style: none;
  margin: 0;
  padding: 4px 4px 12px;
  scroll-snap-type: x proximity;
}
.supporting-images.few-images {
  --supporting-height: 220px;
}
.supporting-images li {
  flex: 0 0 auto;
  min-width: 0;
  scroll-snap-align: start;
}
.supporting-image {
  border-radius: 20px;
}
.supporting-image :deep(img) {
  display: block;
  width: auto;
  height: var(--supporting-height);
  border-radius: 20px;
}
.supporting-image :deep(.media-image-fallback) {
  width: 120px;
  height: var(--supporting-height);
}
.personal-slot {
  min-width: 0;
  padding-top: 24px;
  border-top: 1px solid var(--base-color-border);
}
button:focus-visible,
a:focus-visible,
.supporting-images:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 4px;
}
@media (max-width: 760px) {
  .hero-grid {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: 'copy' 'lead' 'synopsis';
    grid-template-rows: auto;
    gap: 24px;
  }
  .hero-grid.without-images {
    grid-template-areas: 'copy' 'synopsis';
  }
  h1,
  h1.long-title {
    font-size: clamp(2.2rem, 8vw, 3.6rem);
  }
  .lead-image {
    width: fit-content;
    justify-self: center;
  }
  .lead-image img {
    width: auto;
    max-width: 100%;
    max-height: 440px;
  }
  .supporting-images,
  .supporting-images.few-images {
    --supporting-height: 140px;
  }
  .supporting-images {
    display: flex;
    overflow-x: auto;
    gap: 16px;
    scroll-snap-type: x proximity;
  }
  .supporting-image :deep(img) {
    height: var(--supporting-height);
  }
  .supporting-image :deep(.media-image-fallback) {
    width: 94px;
    height: var(--supporting-height);
  }
}
@media (max-width: 576px) {
  .movie-hero {
    border-radius: 28px;
  }
  .lead-image img {
    max-height: 380px;
  }
}
</style>
