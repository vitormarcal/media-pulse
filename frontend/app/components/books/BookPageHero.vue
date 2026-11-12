<template>
  <section class="book-hero">
    <NuxtLink class="back-link" :to="backLink">{{ backLabel }}</NuxtLink>
    <div v-if="detail" class="detail-grid">
      <div class="detail-identification">
        <h1>{{ title }}</h1>
        <div v-if="authors.length" class="authors-row">
          <NuxtLink v-for="author in authors" :key="author.id" :to="author.href">{{ author.name }}</NuxtLink>
        </div>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
      </div>
      <div class="detail-cover">
        <button
          v-if="resolvedCoverUrl && !coverFailed"
          class="cover-button"
          type="button"
          :aria-label="`Ampliar capa de ${title}`"
          @click="gallery?.open(0)"
        >
          <img :src="resolvedCoverUrl" :alt="`Capa de ${title}`" @error="coverFailed = true" />
        </button>
        <div
          v-else
          class="detail-fallback"
          role="img"
          :aria-label="coverFailed ? 'Capa indisponível' : 'Livro sem capa'"
        >
          {{ title.slice(0, 1) }}
        </div>
        <div class="cover-actions">
          <button v-if="resolvedCoverUrl && !coverFailed" class="view-cover" type="button" @click="gallery?.open(0)">
            Ver capa</button
          ><slot name="cover-actions" />
        </div>
        <MediaImageGallery
          ref="gallery"
          :images="coverUrl && !coverFailed ? [coverUrl] : []"
          :title="title"
          viewer-only
        />
      </div>
      <div v-if="description" class="detail-synopsis synopsis">
        <p class="description">{{ expanded ? description : descriptionPreview }}</p>
        <button v-if="description.length > 320" type="button" :aria-expanded="expanded" @click="expanded = !expanded">
          {{ expanded ? 'Ler menos' : 'Ler mais' }}
        </button>
      </div>
    </div>
    <div v-else class="hero-grid">
      <div class="cover-frame">
        <MediaImage
          :src="resolvedCoverUrl"
          :alt="title"
          :fallback="title.slice(0, 1)"
          fallback-class="cover-fallback"
        />
      </div>
      <div class="copy">
        <h1>{{ title }}</h1>
        <div v-if="authors.length" class="authors-row">
          <NuxtLink v-for="author in authors" :key="author.id" :to="author.href">{{ author.name }}</NuxtLink>
        </div>
        <p v-if="subtitle" class="subtitle">{{ subtitle }}</p>
        <div v-if="heroMeta.length" class="meta-list">
          <span v-for="item in heroMeta" :key="item">{{ item }}</span>
        </div>
        <div v-if="description" class="synopsis">
          <p class="description">{{ expanded ? description : descriptionPreview }}</p>
          <button v-if="description.length > 320" type="button" :aria-expanded="expanded" @click="expanded = !expanded">
            {{ expanded ? 'Ler menos' : 'Ler mais' }}
          </button>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'
import type { AuthorLinkModel } from '~/types/books'
const props = withDefaults(
  defineProps<{
    detail?: boolean
    backLink?: string
    backLabel?: string
    title: string
    authors: AuthorLinkModel[]
    subtitle: string | null
    description: string | null
    coverUrl: string | null
    heroMeta: string[]
  }>(),
  { detail: false, backLink: '/books', backLabel: 'Voltar para livros' },
)
const { resolveMediaUrl } = useMediaUrl()
const resolvedCoverUrl = computed(() => resolveMediaUrl(props.coverUrl))
const expanded = ref(false)
const coverFailed = ref(false)
const gallery = ref<InstanceType<typeof MediaImageGallery> | null>(null)
watch(
  () => props.coverUrl,
  () => {
    coverFailed.value = false
  },
)
watch(
  () => props.description,
  () => {
    expanded.value = false
  },
)
const descriptionPreview = computed(() => {
  const description = props.description || ''
  return description.length > 320 ? `${description.slice(0, 320).replace(/\s+\S*$/, '')}…` : description
})
</script>

<style scoped>
.book-hero {
  display: grid;
  gap: 24px;
}
.back-link {
  width: fit-content;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}
a,
button {
  color: var(--base-color-text-primary);
}
a:hover {
  text-decoration: underline;
}
.hero-grid {
  display: grid;
  grid-template-columns: 200px minmax(0, 1fr);
  gap: 32px;
  align-items: start;
}
.copy {
  display: grid;
  gap: 12px;
  min-width: 0;
}
h1 {
  margin: 0;
  font-size: clamp(1.75rem, 4vw, 2.5rem);
  line-height: 1.15;
  letter-spacing: -0.04em;
  overflow-wrap: anywhere;
}
.authors-row,
.meta-list {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 16px;
}
.subtitle,
.description {
  margin: 0;
  color: var(--base-color-text-secondary);
}
.subtitle,
.meta-list {
  font-size: 0.88rem;
}
.description {
  max-width: 44rem;
  line-height: 1.6;
  white-space: pre-line;
}
.synopsis {
  margin-top: 8px;
}
button {
  background: var(--base-color-surface-warm);
  border: 0;
  border-radius: 16px;
  padding: 8px 14px;
  margin-top: 8px;
  font: inherit;
  font-size: 0.8rem;
  cursor: pointer;
}
a:focus-visible,
button:focus-visible {
  outline: 2px solid var(--base-color-focus);
  outline-offset: 4px;
}
.cover-frame {
  overflow: hidden;
  border-radius: 16px;
  background: var(--base-color-surface-soft);
}
.cover-frame img {
  display: block;
  width: 100%;
  height: auto;
}
.cover-fallback {
  display: grid;
  place-items: center;
  aspect-ratio: 2 / 3;
  font-size: 4rem;
  color: var(--base-color-text-secondary);
}
@media (max-width: 575px) {
  .hero-grid {
    grid-template-columns: 1fr;
    gap: 20px;
  }
  .cover-frame {
    width: 128px;
  }
}

.detail-grid {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(180px, 260px);
  grid-template-areas: 'identity cover' 'synopsis cover';
  grid-template-rows: auto 1fr;
  gap: 24px 40px;
  align-items: start;
  padding: clamp(20px, 3vw, 32px);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
}
.detail-identification {
  grid-area: identity;
  display: grid;
  gap: 12px;
  min-width: 0;
}
.detail-grid h1 {
  font-size: clamp(2rem, 4.5vw, 3.5rem);
  line-height: 1.06;
}
.detail-cover {
  grid-area: cover;
  min-width: 0;
}
.cover-button {
  display: block;
  width: fit-content;
  padding: 0;
  margin: 0 auto;
  border-radius: 20px;
  background: transparent;
  cursor: zoom-in;
}
.cover-button img {
  max-width: 100%;
  max-height: 400px;
  width: auto;
  height: auto;
  border-radius: 20px;
}
.cover-actions {
  display: flex;
  flex-wrap: wrap;
  align-items: start;
  gap: 12px;
  margin-top: 12px;
}
.cover-actions button {
  margin-top: 0;
  min-height: 44px;
}
.detail-fallback {
  display: grid;
  place-items: center;
  width: min(100%, 220px);
  aspect-ratio: 2/3;
  margin: 0 auto;
  border-radius: 20px;
  background: var(--base-color-surface-soft);
  color: var(--base-color-text-secondary);
  font-size: 4rem;
}
.detail-grid .detail-synopsis {
  grid-area: synopsis;
  min-width: 0;
  margin: 0;
}
.detail-grid .description {
  color: var(--base-color-text-primary);
  font-size: 0.95rem;
  line-height: 1.65;
}
@media (max-width: 720px) {
  .detail-grid {
    grid-template-columns: minmax(0, 1fr);
    grid-template-areas: 'identity' 'cover' 'synopsis';
    grid-template-rows: auto;
    gap: 24px;
  }
  .detail-cover {
    max-width: 260px;
    width: 100%;
    justify-self: center;
  }
  .cover-button img {
    max-height: 360px;
  }
}
</style>
