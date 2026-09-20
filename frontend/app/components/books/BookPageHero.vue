<template>
  <section class="book-hero">
    <NuxtLink class="back-link" to="/books">← Livros</NuxtLink>
    <div class="hero-grid">
      <div class="cover-frame">
        <img v-if="resolvedCoverUrl" :src="resolvedCoverUrl" :alt="title" />
        <div v-else class="cover-fallback">{{ title.slice(0, 1) }}</div>
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
import type { AuthorLinkModel } from '~/types/books'
const props = defineProps<{
  title: string
  authors: AuthorLinkModel[]
  subtitle: string | null
  description: string | null
  coverUrl: string | null
  heroMeta: string[]
}>()
const { resolveMediaUrl } = useMediaUrl()
const resolvedCoverUrl = computed(() => resolveMediaUrl(props.coverUrl))
const expanded = ref(false)
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
  font-size: 0.88rem;
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
</style>
