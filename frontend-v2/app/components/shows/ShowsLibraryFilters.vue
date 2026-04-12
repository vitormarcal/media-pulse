<template>
  <section class="library-filters">
    <form class="search-form" @submit.prevent="submitSearch">
      <label class="search-label" for="shows-library-query">Buscar na biblioteca</label>
      <div class="search-row">
        <input
          id="shows-library-query"
          v-model="localQuery"
          type="search"
          class="search-input"
          placeholder="Título, nome original..."
        >
        <button type="submit" class="search-button">
          Buscar
        </button>
      </div>
    </form>

    <div class="years">
      <NuxtLink
        class="year-chip"
        :class="{ active: selectedYear == null && !query }"
        to="/shows/library"
      >
        Tudo
      </NuxtLink>

      <NuxtLink
        v-for="year in years"
        :key="year.year"
        class="year-chip"
        :class="{ active: selectedYear === year.year }"
        :to="`/shows/library?year=${year.year}`"
      >
        <span>{{ year.label }}</span>
        <strong>{{ year.watches }}</strong>
      </NuxtLink>
    </div>
  </section>
</template>

<script setup lang="ts">
import type { ShowLibraryYearChip } from '~/types/shows'

const props = defineProps<{
  query: string
  selectedYear: number | null
  years: ShowLibraryYearChip[]
}>()

const localQuery = ref(props.query)

watch(() => props.query, (value) => {
  localQuery.value = value
})

function submitSearch() {
  const trimmed = localQuery.value.trim()

  if (!trimmed) {
    navigateTo('/shows/library')
    return
  }

  navigateTo(`/shows/library?q=${encodeURIComponent(trimmed)}`)
}
</script>

<style scoped>
.library-filters {
  display: grid;
  gap: 18px;
}

.search-form {
  display: grid;
  gap: 10px;
}

.search-label {
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
}

.search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
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
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
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
  background: color-mix(in srgb, var(--base-color-brand-red) 14%, white);
}

@media (max-width: 720px) {
  .search-row {
    grid-template-columns: 1fr;
  }
}
</style>
