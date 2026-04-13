<template>
  <section class="library-filters">
    <div class="kind-tabs">
      <NuxtLink
        v-for="tab in tabs"
        :key="tab.kind"
        class="kind-tab"
        :class="{ active: selectedKind === tab.kind }"
        :to="tabLink(tab.kind)"
      >
        <span>{{ tab.label }}</span>
        <strong>{{ tab.summary }}</strong>
      </NuxtLink>
    </div>

    <form class="search-form" @submit.prevent="submitSearch">
      <label class="search-label" for="music-library-query">Buscar na biblioteca</label>
      <div class="search-row">
        <input
          id="music-library-query"
          v-model="localQuery"
          type="search"
          class="search-input"
          placeholder="Artista, álbum, faixa..."
        >
        <button type="submit" class="search-button">
          Buscar
        </button>
      </div>
    </form>
  </section>
</template>

<script setup lang="ts">
import type { MusicLibraryKind, MusicLibraryTab } from '~/types/music'

const props = defineProps<{
  query: string
  selectedKind: MusicLibraryKind
  tabs: MusicLibraryTab[]
}>()

const localQuery = ref(props.query)

watch(() => props.query, (value) => {
  localQuery.value = value
})

function tabLink(kind: MusicLibraryKind) {
  return props.query
    ? `/music/library?kind=${kind}&q=${encodeURIComponent(props.query)}`
    : `/music/library?kind=${kind}`
}

function submitSearch() {
  const trimmed = localQuery.value.trim()

  if (!trimmed) {
    navigateTo(`/music/library?kind=${props.selectedKind}`)
    return
  }

  navigateTo(`/music/library?kind=${props.selectedKind}&q=${encodeURIComponent(trimmed)}`)
}
</script>

<style scoped>
.library-filters {
  display: grid;
  gap: 18px;
  padding: 20px;
  border-radius: 32px;
  background:
    linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 48%, white);
}

.kind-tabs {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.kind-tab {
  display: grid;
  gap: 2px;
  padding: 10px 14px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-warm) 82%, white);
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
}

.kind-tab strong {
  color: var(--base-color-text-primary);
  font-size: 0.72rem;
}

.kind-tab.active {
  background: color-mix(in srgb, var(--base-color-brand-red) 16%, white);
  color: var(--base-color-text-primary);
}

.search-form {
  display: grid;
  gap: 10px;
}

.search-label {
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
  font-weight: 700;
}

.search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: center;
}

.search-input {
  width: 100%;
  padding: 12px 16px;
  border: 1px solid var(--base-color-text-muted);
  border-radius: 16px;
  background: #fff;
  color: var(--base-color-text-primary);
}

.search-button {
  padding: 8px 16px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-brand-red);
  color: #000;
  cursor: pointer;
}

@media (max-width: 720px) {
  .search-row {
    grid-template-columns: 1fr;
  }
}
</style>
