<template>
  <section class="library-filters">
    <form class="search-form" @submit.prevent="submitSearch">
      <label class="search-label" for="music-library-query">Buscar na biblioteca</label>
      <div class="search-row">
        <input
          id="music-library-query"
          v-model="localQuery"
          type="search"
          class="search-input"
          placeholder="Artista, álbum, faixa..."
        />
        <button type="submit" class="search-button">Buscar</button>
      </div>
    </form>

    <div class="years-block">
      <p class="group-label">Recortes anuais</p>

      <div class="years">
        <NuxtLink class="year-chip" :class="{ active: selectedYear == null && !query }" to="/music/library?kind=albums">
          <span>Tudo</span>
          <strong>arquivo</strong>
        </NuxtLink>

        <NuxtLink
          v-for="year in years"
          :key="year.year"
          class="year-chip"
          :class="{ active: selectedYear === year.year }"
          :to="`/music/library?year=${year.year}`"
        >
          <span>{{ year.label }}</span>
          <strong>{{ year.detail }}</strong>
        </NuxtLink>
      </div>
    </div>

    <div class="tabs-block">
      <p class="group-label">Camadas da biblioteca</p>

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
    </div>
  </section>
</template>

<script setup lang="ts">
import type { MusicLibraryKind, MusicLibraryTab, MusicLibraryYearChip } from '~/types/music'

const props = defineProps<{
  query: string
  selectedKind: MusicLibraryKind
  selectedYear: number | null
  tabs: MusicLibraryTab[]
  years: MusicLibraryYearChip[]
}>()

const localQuery = ref(props.query)

watch(
  () => props.query,
  (value) => {
    localQuery.value = value
  },
)

function tabLink(kind: MusicLibraryKind) {
  if (props.selectedYear) {
    return `/music/library?year=${props.selectedYear}&kind=${kind}`
  }

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
  gap: 20px;
  padding: 20px;
  border-radius: 32px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 48%, white);
}

.search-form {
  display: grid;
  gap: 10px;
}

.years-block,
.tabs-block {
  display: grid;
  gap: 10px;
}

.group-label {
  margin: 0;
  color: var(--base-color-text-muted);
  font-size: 0.76rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.years {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.year-chip {
  display: grid;
  gap: 2px;
  padding: 10px 14px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 74%, white);
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
}

.year-chip strong {
  color: var(--base-color-text-primary);
  font-size: 0.72rem;
}

.year-chip.active {
  background: color-mix(in srgb, var(--base-color-brand-red) 16%, white);
  color: var(--base-color-text-primary);
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
