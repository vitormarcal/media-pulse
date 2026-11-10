<template>
  <section class="movie-hero">
    <div class="hero-topbar">
      <NuxtLink class="back-link" to="/movies"> Voltar para filmes </NuxtLink>
    </div>

    <div class="hero-grid" :class="{ 'without-images': !gallery.length }">
      <div class="copy">
        <p class="eyebrow">Filme</p>
        <h1 ref="heading" tabindex="-1">{{ title }}</h1>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
        <p v-if="description" class="description">{{ description }}</p>
      </div>

      <div v-if="gallery.length" class="gallery-preview">
        <div class="gallery" :class="`images-${previewImages.length}`">
          <button
            v-for="(image, index) in previewImages"
            :key="`${image}-${index}`"
            type="button"
            class="gallery-item"
            :class="`slot-${index}`"
            :aria-label="`Ampliar imagem ${index + 1} de ${title}`"
            @click="$emit('viewImage', index)"
          >
            <MediaImage :src="resolveMediaUrl(image)" :alt="`${index === 0 ? 'Capa' : 'Imagem'} de ${title}`" />
          </button>
        </div>
        <button ref="viewAllButton" type="button" class="view-all" @click="$emit('viewImage', 0)">
          {{ imageCount === 1 ? 'Ver imagem' : `Ver todas as ${imageCount} imagens` }}
        </button>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
const props = defineProps<{
  title: string
  subtitle: string | null
  description: string | null
  gallery: string[]
  imageCount: number
}>()

defineEmits<{ viewImage: [index: number] }>()
const viewAllButton = ref<HTMLButtonElement>()
const heading = ref<HTMLHeadingElement>()
defineExpose({ focusGallery: () => (viewAllButton.value || heading.value)?.focus() })
const { resolveMediaUrl } = useMediaUrl()
const previewImages = computed(() => (props.gallery.length <= 2 ? props.gallery.slice(0, 1) : props.gallery))
</script>

<style scoped>
.movie-hero {
  display: grid;
  gap: 18px;
}

.hero-topbar {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
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
  background-image:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.98));
  background-size: cover;
  background-position: center;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
}

.copy {
  display: grid;
  align-content: center;
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
  font-size: clamp(2rem, 4.2vw, 3.5rem);
  line-height: 0.92;
  letter-spacing: -0.04em;
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

.gallery-preview {
  display: grid;
  align-content: center;
  gap: 12px;
  min-width: 0;
}
.gallery {
  display: grid;
  grid-template-columns: minmax(0, 1.5fr) minmax(0, 1fr);
  grid-template-rows: repeat(3, minmax(0, 1fr));
  gap: 12px;
  height: clamp(240px, 26vw, 340px);
}
.gallery.images-1 {
  grid-template-columns: minmax(0, 1fr);
}
.gallery.images-1 :deep(img) {
  object-fit: contain;
}
.gallery.images-2 {
  grid-template-rows: minmax(0, 1fr);
}
.gallery.images-3 {
  grid-template-rows: repeat(2, minmax(0, 1fr));
}
.view-all {
  justify-self: end;
  min-height: 44px;
  border: 0;
  border-radius: 16px;
  padding: 10px 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.88rem;
  font-weight: 700;
  cursor: pointer;
}
.view-all:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 4px;
}

.gallery-item {
  border: 0;
  padding: 0;
  cursor: zoom-in;
  min-width: 0;
  min-height: 0;
  overflow: hidden;
  border-radius: 28px;
  background: var(--base-color-surface-warm);
}

.gallery-item:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 4px;
}

.gallery-item :deep(img) {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.slot-0 {
  grid-row: 1 / -1;
}

@media (max-width: 980px) {
  .hero-topbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .hero-grid {
    grid-template-columns: 1fr;
  }
  .gallery {
    height: clamp(240px, 50vw, 320px);
  }
}
.hero-grid.without-images {
  grid-template-columns: minmax(0, 1fr);
}
</style>
