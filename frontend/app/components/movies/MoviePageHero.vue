<template>
  <section class="movie-hero">
    <div class="hero-topbar">
      <NuxtLink class="back-link" to="/movies"> Voltar para filmes </NuxtLink>
    </div>

    <div class="hero-grid" :class="{ 'without-images': !gallery.length, 'with-personal': !!$slots.personal }">
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

      <div v-if="$slots.personal" class="personal-slot"><slot name="personal" /></div>

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
const descriptionExpanded = ref(false)
const descriptionPreview = computed(() => {
  const description = props.description || ''
  return description.length > 320 ? `${description.slice(0, 320).replace(/\s+\S*$/, '')}…` : description
})
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
  grid-template-columns: minmax(0, 1fr) minmax(260px, 0.75fr);
  grid-template-areas: 'copy gallery' 'personal gallery';
  grid-template-rows: auto 1fr;
  align-items: start;
  gap: 24px 40px;
  padding: clamp(20px, 3vw, 32px);
  border-radius: 40px;
  background: var(--base-color-surface-strong);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 35%, white);
}

.copy {
  grid-area: copy;
  min-width: 0;
  display: grid;
  align-content: start;
  gap: 12px;
}

h1 {
  margin: 0;
  font-size: clamp(2rem, 3.8vw, 3rem);
  overflow-wrap: anywhere;
  line-height: 1.06;
  letter-spacing: -0.04em;
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
.synopsis-toggle {
  justify-self: start;
  border: 0;
  padding: 8px 0;
  background: transparent;
  color: var(--base-color-text-primary);
  font: inherit;
  font-size: 0.88rem;
  font-weight: 600;
  text-decoration: underline;
  cursor: pointer;
}
.synopsis-toggle:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
.description {
  max-width: 42rem;
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}

.personal-slot {
  grid-area: personal;
  min-width: 0;
}
.gallery-preview {
  grid-area: gallery;
  align-self: start;
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
  display: flex;
  justify-content: center;
  height: auto;
}
.gallery.images-1 .gallery-item {
  display: flex;
  max-width: 100%;
  background: transparent;
}
.gallery.images-1 :deep(img) {
  width: auto;
  height: auto;
  max-width: 100%;
  max-height: 440px;
  border-radius: 28px;
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

@media (max-width: 760px) {
  .hero-topbar {
    align-items: flex-start;
    flex-direction: column;
  }
  .hero-grid {
    grid-template-columns: 1fr;
    grid-template-areas: 'copy' 'gallery' 'personal';
    gap: 24px;
  }
  .gallery {
    height: clamp(240px, 50vw, 320px);
  }
}
@media (max-width: 576px) {
  .gallery.images-1 :deep(img) {
    max-height: 380px;
  }
}
.hero-grid.without-images {
  grid-template-columns: minmax(0, 1fr);
  grid-template-areas: 'copy' 'personal';
}
.hero-grid:not(.with-personal) {
  grid-template-rows: auto;
}
.hero-grid.without-images:not(.with-personal) {
  grid-template-areas: 'copy';
}
</style>
