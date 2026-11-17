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
    <nav class="hero-topbar" aria-label="Navegação do filme">
      <NuxtLink class="back-link" :to="backTo">{{ backLabel }}</NuxtLink>
    </nav>

    <div class="hero-grid" :class="{ 'without-images': !leadImage || leadFailed }">
      <div class="copy">
        <h1 ref="heading" tabindex="-1">{{ title }}</h1>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
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
      <button
        v-if="leadImage && !leadFailed"
        type="button"
        class="lead-image image-button"
        :aria-label="`Ampliar capa de ${title}`"
        @click="$emit('viewImage', 0)"
      >
        <img :src="resolveMediaUrl(leadImage) || undefined" :alt="`Capa de ${title}`" @error="leadFailed = true" />
      </button>
    </div>

    <ul v-if="supportingImages.length" class="supporting-images" aria-label="Outras imagens do filme" tabindex="0">
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
    backTo?: string
    backLabel?: string
  }>(),
  { backTo: '/movies', backLabel: 'Voltar para filmes' },
)

defineEmits<{ viewImage: [index: number] }>()
const viewAllButton = ref<HTMLButtonElement>()
const heading = ref<HTMLHeadingElement>()
defineExpose({ focusGallery: () => (viewAllButton.value || heading.value)?.focus() })
const { resolveMediaUrl } = useMediaUrl()
const descriptionExpanded = ref(false)
const leadFailed = ref(false)
const leadImage = computed(() => props.gallery[0])
const supportingImages = computed(() => props.gallery.slice(1, 5))
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
  opacity: 0.08;
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
  align-items: center;
  gap: 32px;
}
.hero-grid.without-images {
  grid-template-columns: minmax(0, 1fr);
}
.copy {
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
.subtitle {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 1.02rem;
}
.synopsis {
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
  width: 100%;
}
.lead-image img {
  display: block;
  width: 100%;
  height: auto;
  border-radius: 28px;
}
.supporting-images {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  align-items: start;
  gap: 20px;
  list-style: none;
  margin: 0;
  padding: 4px;
}
.supporting-images li {
  min-width: 0;
}
.supporting-image {
  width: 100%;
  border-radius: 20px;
}
.supporting-image :deep(img) {
  display: block;
  width: 100%;
  height: auto;
  border-radius: 20px;
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
    gap: 24px;
  }
  h1 {
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
  .supporting-images {
    display: flex;
    overflow-x: auto;
    gap: 16px;
    scroll-snap-type: x proximity;
  }
  .supporting-images li {
    flex: 0 0 38%;
    scroll-snap-align: start;
  }
}
@media (max-width: 576px) {
  .movie-hero {
    border-radius: 28px;
  }
  .lead-image img {
    max-height: 380px;
  }
  .supporting-images li {
    flex-basis: 34%;
  }
}
</style>
