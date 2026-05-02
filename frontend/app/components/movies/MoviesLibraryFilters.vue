<template>
  <section class="library-filters">
    <form class="search-form" @submit.prevent="submitSearch">
      <label class="search-label" for="movies-library-query">Buscar filmes</label>
      <div class="search-row">
        <input
          id="movies-library-query"
          v-model="localQuery"
          type="search"
          class="search-input"
          placeholder="Título, nome original..."
        />
        <button type="submit" class="search-button">Buscar</button>
      </div>
    </form>

    <div class="years">
      <NuxtLink
        class="year-chip"
        :class="{ active: selectedYear == null && !query && !selectedUnwatched }"
        to="/movies"
      >
        Tudo
      </NuxtLink>

      <NuxtLink
        class="year-chip"
        :class="{ active: selectedUnwatched }"
        :to="query ? `/movies?q=${encodeURIComponent(query)}&unwatched=1` : '/movies?unwatched=1'"
      >
        <span>Não vistos</span>
        <strong>sem sessão</strong>
      </NuxtLink>

      <NuxtLink
        v-for="year in years"
        :key="year.year"
        class="year-chip"
        :class="{ active: selectedYear === year.year }"
        :to="query ? `/movies?q=${encodeURIComponent(query)}&year=${year.year}` : `/movies?year=${year.year}`"
      >
        <span>{{ year.label }}</span>
        <strong>{{ year.watches }}</strong>
      </NuxtLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { MovieLibraryYearChip } from '~/types/movies'

const props = defineProps<{
  query: string
  selectedYear: number | null
  selectedUnwatched: boolean
  years: MovieLibraryYearChip[]
}>()

const localQuery = ref(props.query)

watch(
  () => props.query,
  (value) => {
    localQuery.value = value
  },
)

function submitSearch() {
  const trimmed = localQuery.value.trim()
  const suffix = props.selectedUnwatched ? '&unwatched=1' : ''

  if (!trimmed) {
    navigateTo(props.selectedUnwatched ? '/movies?unwatched=1' : '/movies')
    return
  }

  navigateTo(`/movies?q=${encodeURIComponent(trimmed)}${suffix}`)
}
</script>

<style scoped>
.library-filters {
  display: grid;
  gap: 18px;
  padding: 20px;
  border-radius: 32px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.94), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 48%, white);
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
  background: color-mix(in srgb, var(--base-color-surface-warm) 82%, white);
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

@media (max-width: 720px) {
  .search-row {
    grid-template-columns: 1fr;
  }
}
</style>
