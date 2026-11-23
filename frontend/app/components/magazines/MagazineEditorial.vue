<template>
  <section class="mag-editorial mag-panel">
    <img v-if="leadImage" class="backdrop" :src="leadImage" alt="" aria-hidden="true" />
    <div class="mag-actions">
      <NuxtLink class="mag-button" to="/">Voltar para a capa</NuxtLink>
      <slot name="actions" />
    </div>
    <div class="editorial-layout">
      <h1>{{ overview?.inProgress.length ? 'Revistas em leitura' : 'Últimas leituras' }}</h1>
      <NuxtLink v-if="lead" :to="lead.href" class="lead" :class="{ 'without-cover': !leadImage }">
        <img v-if="leadImage" class="lead-cover" :src="leadImage" :alt="lead.title" @error="failedLead = leadImage" />
        <div class="lead-copy">
          <p>{{ lead.label }}</p>
          <h2>{{ lead.title }}</h2>
          <p>{{ lead.subtitle }}</p>
        </div>
      </NuxtLink>
      <p v-else-if="!error" class="mag-muted">Nenhuma leitura em andamento ou concluída.</p>
    </div>
    <p v-if="error" class="mag-error" role="alert">
      Não foi possível carregar as leituras.
      <button class="mag-button" @click="$emit('retry')">Tentar novamente</button>
    </p>
    <div v-if="supporting.length" class="supporting" tabindex="0" aria-label="Outros números com leitura registrada">
      <NuxtLink v-for="item in supporting" :key="item.id" :to="item.href" class="supporting-card">
        <div class="supporting-cover">
          <MediaImage :src="resolveMediaUrl(item.imageUrl)" :alt="item.title" loading="lazy" />
        </div>
        <div>
          <h3>{{ item.title }}</h3>
          <p>{{ item.subtitle }}</p>
          <p>{{ item.label }}</p>
        </div>
      </NuxtLink>
    </div>
    <form class="editorial-search" @submit.prevent="search">
      <label for="mag-editorial-query">Buscar revista ou número</label>
      <div class="search-row">
        <input id="mag-editorial-query" v-model="query" type="search" placeholder="Nome, número, mês/ano ou ISSN" />
        <button class="mag-button primary" type="submit">Buscar</button>
      </div>
    </form>
  </section>
</template>
<script setup lang="ts">
import type { MagazineOverview, MagazineIssue } from '~/types/magazines'
import { magazineDate, magazineNumber } from '~/utils/magazines'
const props = defineProps<{ overview: MagazineOverview | null; error: boolean }>()
defineEmits<{ retry: [] }>()
const { resolveMediaUrl } = useMediaUrl()
const query = ref('')
const failedLead = ref<string | null>(null)
function entry(issue: MagazineIssue, label: string) {
  return {
    id: issue.id,
    title: issue.publication.name,
    subtitle: magazineNumber(issue),
    imageUrl: issue.coverUrl,
    href: `/magazines/${issue.id}`,
    label,
  }
}
const entries = computed(() => {
  const overview = props.overview
  if (!overview) return []
  const candidates = [
    ...overview.inProgress.map((issue) =>
      entry(
        issue,
        issue.latestRead?.currentPage != null && issue.totalPages
          ? `Lendo · página ${issue.latestRead.currentPage} de ${issue.totalPages}`
          : `Lendo · ${Math.round(issue.latestRead?.progressPct ?? 0)}%`,
      ),
    ),
    ...overview.recent.map(({ issue, read }) => entry(issue, `Lida em ${magazineDate(read.finishedAt)}`)),
  ]
  const seen = new Set<number>()
  return candidates
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
  const url = resolveMediaUrl(lead.value?.imageUrl)
  return url && url !== failedLead.value ? url : null
})
async function search() {
  await navigateTo({ path: '/magazines', query: { q: query.value.trim() || undefined, view: 'archive' } })
}
</script>
<style scoped src="../../assets/css/magazines.css"></style>
<style scoped>
.mag-editorial {
  position: relative;
  overflow: hidden;
  display: grid;
  gap: 28px;
  isolation: isolate;
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
.editorial-layout {
  display: grid;
  grid-template-columns: minmax(0, 0.8fr) minmax(0, 1.2fr);
  align-items: center;
  gap: 28px;
}
h1 {
  margin: 0;
  font-size: clamp(3rem, 7vw, 6rem);
  line-height: 0.98;
  letter-spacing: -0.06em;
  overflow-wrap: anywhere;
}
.lead {
  display: grid;
  grid-template-columns: minmax(0, 1.6fr) minmax(0, 1fr);
  gap: 20px;
  align-items: center;
  padding: 16px;
  border-radius: 28px;
  background: white;
  color: var(--base-color-text-primary);
  text-decoration: none;
}
.lead.without-cover {
  grid-template-columns: 1fr;
}
.lead-cover {
  width: 100%;
  height: auto;
  border-radius: 16px;
}
.lead-copy {
  min-width: 0;
  overflow-wrap: anywhere;
}
h2 {
  font-size: clamp(1.4rem, 2.4vw, 2.2rem);
  margin: 8px 0;
}
p {
  color: var(--base-color-text-secondary);
  margin: 8px 0;
}
.supporting {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
}
.supporting-card {
  min-width: 0;
  color: var(--base-color-text-primary);
  text-decoration: none;
}
.supporting-cover {
  aspect-ratio: 2 / 3;
  padding: 8px;
  border-radius: 20px;
  background: white;
}
.supporting-cover :deep(img),
.supporting-cover :deep(.media-image-fallback) {
  width: 100%;
  height: 100%;
  object-fit: contain;
  border-radius: 12px;
}
h3 {
  margin: 12px 0 0;
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
input {
  min-width: 0;
  width: 100%;
  padding: 11px 15px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
  color: var(--base-color-text-primary);
}
:focus-visible {
  outline: 3px solid var(--base-color-focus);
  outline-offset: 3px;
}
@media (max-width: 900px) {
  .editorial-layout {
    grid-template-columns: 1fr;
  }
  .lead-cover {
    width: auto;
    max-width: 100%;
    max-height: 520px;
    justify-self: center;
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
  .lead {
    grid-template-columns: 1fr;
  }
  .lead-cover {
    max-height: 340px;
  }
  .supporting-card {
    flex: 0 0 68%;
  }
  .search-row {
    flex-wrap: wrap;
  }
}
</style>
