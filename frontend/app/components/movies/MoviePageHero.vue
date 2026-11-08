<template>
  <section class="movie-hero">
    <div class="hero-topbar">
      <NuxtLink class="back-link" to="/movies"> Voltar para filmes </NuxtLink>
    </div>

    <div class="hero-grid" :class="{ 'without-images': !gallery.length }" :style="heroShellStyle">
      <div class="copy">
        <p class="eyebrow">Filme</p>
        <h1 ref="heading" tabindex="-1">{{ title }}</h1>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
        <p v-if="description" class="description">{{ description }}</p>

        <div class="meta-list">
          <span v-for="item in heroMeta" :key="item" class="meta-pill">{{ item }}</span>
        </div>

        <div
          v-if="identifiers.length || companies.visibleCount || terms.visibleCount || editingCompanies || editingTerms"
          class="detail-stack"
        >
          <div v-if="identifiers.length" class="detail-row">
            <p class="detail-label">IDs</p>
            <div class="detail-pills">
              <span v-for="identifier in identifiers" :key="identifier.id" class="detail-pill identifier-pill">
                {{ identifier.provider }} {{ identifier.externalId }}
              </span>
            </div>
          </div>

          <div v-if="companies.visibleCount || editingCompanies" class="detail-row companies-row">
            <MovieCompaniesPanel
              :movie-id="movieId"
              :companies="companies"
              :enrichment-status="enrichment.companies"
              :editing="editingCompanies"
              @changed="$emit('companiesChanged')"
            />
          </div>

          <div v-if="terms.visibleCount || editingTerms" class="detail-row terms-row">
            <MovieTermsPanel
              :movie-id="movieId"
              :terms="terms"
              :enrichment-status="enrichment.terms"
              :editing="editingTerms"
              embedded
              @changed="$emit('termsChanged')"
            />
          </div>
        </div>
      </div>

      <div v-if="gallery.length" class="gallery-preview">
        <div class="gallery" :class="`images-${gallery.length}`">
          <button
            v-for="(image, index) in gallery"
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
import MovieCompaniesPanel from '~/components/movies/MovieCompaniesPanel.vue'
import MovieTermsPanel from '~/components/movies/MovieTermsPanel.vue'
import type { MoviePageData } from '~/types/movies'

const props = defineProps<{
  movieId: number
  editingCompanies: boolean
  editingTerms: boolean
  title: string
  subtitle: string | null
  description: string | null
  gallery: string[]
  imageCount: number
  heroMeta: string[]
  identifiers: MoviePageData['identifiers']
  companies: MoviePageData['companies']
  terms: MoviePageData['terms']
  enrichment: MoviePageData['enrichment']
}>()

defineEmits<{ viewImage: [index: number]; companiesChanged: []; termsChanged: [] }>()
const viewAllButton = ref<HTMLButtonElement>()
const heading = ref<HTMLHeadingElement>()
defineExpose({ focusGallery: () => (viewAllButton.value || heading.value)?.focus() })
const { resolveMediaUrl } = useMediaUrl()
const heroImageUrl = computed(() => props.gallery.map((image) => resolveMediaUrl(image)).find(Boolean) ?? null)
const heroShellStyle = computed(() =>
  heroImageUrl.value
    ? {
        backgroundImage: `linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(246, 243, 238, 0.97)), radial-gradient(circle at top right, rgba(230, 0, 35, 0.1), transparent 28%), url("${heroImageUrl.value}")`,
      }
    : undefined,
)
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

.detail-stack {
  display: grid;
  gap: 14px;
  margin-top: 2px;
}

.detail-row {
  display: grid;
  gap: 8px;
}

.detail-label {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.detail-pills {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.meta-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.detail-pill {
  padding: 8px 12px;
  border-radius: 999px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--base-color-text-secondary);
  font-size: 0.76rem;
}

.terms-row {
  padding-top: 2px;
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
  height: clamp(320px, 38vw, 520px);
}
.gallery.images-1 {
  grid-template-columns: minmax(0, 1fr);
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
    height: clamp(280px, 60vw, 420px);
  }
}
.hero-grid.without-images {
  grid-template-columns: minmax(0, 1fr);
}
</style>
