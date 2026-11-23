<template>
  <section class="art-editorial" aria-labelledby="art-editorial-title">
    <img v-if="leadImage" class="backdrop" :src="leadImage" alt="" aria-hidden="true" />
    <div class="art-actions">
      <NuxtLink class="art-button" to="/">Voltar para a capa</NuxtLink>
      <slot name="actions" />
    </div>
    <div class="editorial-layout">
      <div class="intro">
        <h1 id="art-editorial-title">Obras recentes</h1>
        <p>Por ordem de inclusão no acervo.</p>
      </div>
      <p v-if="pending" role="status">Carregando obras recentes…</p>
      <div v-else-if="error" class="art-error" role="alert">
        Não foi possível carregar as obras recentes.
        <button class="art-button" @click="$emit('retry')">Tentar novamente</button>
      </div>
      <NuxtLink v-else-if="lead" :to="`/artworks/${lead.id}`" class="lead" :class="{ 'without-image': !leadImage }">
        <img v-if="leadImage" class="lead-image" :src="leadImage" :alt="lead.title" @error="failedLead = leadImage" />
        <div class="lead-copy">
          <h2>{{ lead.title }}</h2>
          <p v-if="context(lead)">{{ context(lead) }}</p>
        </div>
      </NuxtLink>
      <p v-else class="art-muted">Nenhuma obra adicionada ao acervo.</p>
    </div>
    <div
      v-if="supporting.length && !pending && !error"
      class="supporting"
      tabindex="0"
      aria-label="Outras obras adicionadas recentemente"
    >
      <NuxtLink v-for="item in supporting" :key="item.id" :to="`/artworks/${item.id}`" class="supporting-card">
        <div class="supporting-image">
          <MediaImage
            :src="resolveMediaUrl(item.imageUrl)"
            :alt="item.title"
            loading="lazy"
            fallback="Imagem indisponível"
          />
        </div>
        <h3>{{ item.title }}</h3>
        <p v-if="context(item)">{{ context(item) }}</p>
      </NuxtLink>
    </div>
    <form class="editorial-search" @submit.prevent="$emit('search', query)">
      <label for="art-editorial-query">Buscar obras</label>
      <div class="search-row">
        <input id="art-editorial-query" v-model="query" type="search" placeholder="Título, artista, técnica…" />
        <button class="art-button art-primary" type="submit" :disabled="searchDisabled">Buscar</button>
      </div>
    </form>
  </section>
</template>
<script setup lang="ts">
import type { ArtworkCard } from '~/types/artworks'
const props = defineProps<{ items: ArtworkCard[]; pending: boolean; error: boolean; searchDisabled: boolean }>()
defineEmits<{ retry: []; search: [query: string] }>()
const { resolveMediaUrl } = useMediaUrl()
const query = ref('')
const failedLead = ref<string | null>(null)
const entries = computed(() => {
  const seen = new Set<number>()
  return props.items
    .filter((item) => {
      if (seen.has(item.id)) return false
      seen.add(item.id)
      return true
    })
    .slice(0, 5)
})
const lead = computed(() => entries.value[0])
const supporting = computed(() => entries.value.slice(1))
const leadImage = computed(() => {
  if (props.error || props.pending) return null
  const url = resolveMediaUrl(lead.value?.imageUrl)
  return url && url !== failedLead.value ? url : null
})
function context(item: ArtworkCard) {
  return [item.artist?.name, item.year].filter((part) => part != null && part !== '').join(' · ')
}
</script>
<style scoped>
.art-editorial {
  position: relative;
  overflow: hidden;
  isolation: isolate;
  display: grid;
  gap: 28px;
  min-width: 0;
  padding: clamp(20px, 4vw, 32px);
  border: 1px solid var(--base-color-border);
  border-radius: 28px;
  background: var(--base-color-surface-strong);
}
.backdrop {
  position: absolute;
  inset: 0;
  width: 100%;
  height: 100%;
  object-fit: cover;
  opacity: 0.08;
  z-index: -1;
  pointer-events: none;
}
.art-editorial :deep(.art-nav) {
  margin: 0;
}
.editorial-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  gap: 28px;
  align-items: center;
}
h1 {
  margin: 0;
  font-size: clamp(3rem, 7vw, 6rem);
  line-height: 0.98;
  letter-spacing: -0.06em;
  overflow-wrap: anywhere;
}
.intro p {
  margin-top: 20px;
}
.lead {
  display: grid;
  gap: 20px;
  min-width: 0;
  padding: 16px;
  border-radius: 28px;
  background: white;
  color: var(--base-color-text-primary);
  text-decoration: none;
}
.lead-image {
  display: block;
  width: 100%;
  height: auto;
  max-height: 620px;
  object-fit: contain;
  border-radius: 12px;
}
.lead-copy {
  min-width: 0;
  overflow-wrap: anywhere;
}
h2 {
  font-size: clamp(1.4rem, 2.4vw, 2.2rem);
  margin: 0;
}
p {
  color: var(--base-color-text-secondary);
  margin: 8px 0 0;
}
.supporting {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
  min-width: 0;
}
.supporting-card {
  min-width: 0;
  color: var(--base-color-text-primary);
  text-decoration: none;
}
.supporting-image {
  aspect-ratio: 4 / 3;
  padding: 8px;
  border-radius: 20px;
  background: white;
}
.supporting-image :deep(img),
.supporting-image :deep(.media-image-fallback) {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 12px;
  box-sizing: border-box;
}
h3 {
  margin: 12px 0 0;
  font-size: 1rem;
  overflow-wrap: anywhere;
}
.supporting-card p {
  font-size: 0.88rem;
  overflow-wrap: anywhere;
}
.editorial-search {
  display: grid;
  gap: 8px;
}
.search-row {
  display: flex;
  gap: 12px;
}
.search-row input {
  flex: 1;
  min-width: 0;
}
:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
@media (max-width: 900px) {
  .editorial-layout {
    grid-template-columns: 1fr;
  }
  .lead-image {
    max-height: 520px;
  }
  .supporting {
    display: flex;
    overflow-x: auto;
    scroll-snap-type: x proximity;
    padding: 4px 4px 12px;
  }
  .supporting-card {
    flex: 0 0 38%;
    scroll-snap-align: start;
  }
}
@media (max-width: 600px) {
  h1 {
    font-size: clamp(2rem, 11vw, 3.5rem);
  }
  .lead-image {
    max-height: 340px;
  }
  .supporting-card {
    flex: 0 0 78%;
  }
  .search-row {
    flex-wrap: wrap;
  }
  .search-row input {
    flex-basis: 100%;
  }
}
</style>
