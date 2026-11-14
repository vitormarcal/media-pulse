<template>
  <section
    class="library-filters"
    :aria-label="
      mode === 'search' ? 'Buscar séries' : mode === 'filters' ? 'Filtrar séries' : 'Buscar e filtrar séries'
    "
  >
    <form v-if="mode !== 'filters'" class="search-form" @submit.prevent="submitSearch">
      <label for="shows-library-query">Buscar no acervo</label>
      <div class="search-row">
        <input id="shows-library-query" v-model="localQuery" type="search" placeholder="Título ou nome original" />
        <button type="submit">Buscar</button>
      </div>
      <p v-if="selectedYear" class="filter-note">
        A busca por título considera todo o acervo e sai do ano selecionado.
      </p>
    </form>
    <div v-if="mode !== 'search'" class="filter-row">
      <nav class="status-filters" aria-label="Episódios registrados">
        <NuxtLink
          :to="query ? { path: '/shows', query: { q: query } } : '/shows'"
          :aria-current="!selectedUnwatched && !selectedYear ? 'page' : undefined"
          >Todos</NuxtLink
        >
        <NuxtLink
          :to="{ path: '/shows', query: { ...(query ? { q: query } : {}), unwatched: '1' } }"
          :aria-current="selectedUnwatched ? 'page' : undefined"
          >Sem episódio registrado</NuxtLink
        >
      </nav>
      <label class="year-filter"
        >Ano da visualização
        <select aria-label="Ano da visualização" :value="selectedYear ?? ''" @change="selectYear">
          <option value="">Todos os anos</option>
          <option v-for="year in years" :key="year.year" :value="year.year">{{ year.label }}</option>
        </select>
      </label>
      <NuxtLink v-if="query || selectedYear || selectedUnwatched" class="clear-filter" to="/shows"
        >Limpar filtros</NuxtLink
      >
    </div>
  </section>
</template>
<script setup lang="ts">
import type { ShowLibraryYearChip } from '~/types/shows'
const props = withDefaults(
  defineProps<{
    query: string
    selectedYear: number | null
    selectedUnwatched: boolean
    years: ShowLibraryYearChip[]
    mode?: 'all' | 'search' | 'filters'
  }>(),
  { mode: 'all' },
)
const localQuery = ref(props.query)
watch(
  () => props.query,
  (value) => {
    localQuery.value = value
  },
)
function submitSearch() {
  navigateTo({
    path: '/shows',
    query: {
      ...(localQuery.value.trim() ? { q: localQuery.value.trim() } : {}),
      ...(props.selectedUnwatched ? { unwatched: '1' } : {}),
    },
  })
}
function selectYear(event: Event) {
  const year = (event.target as HTMLSelectElement).value
  navigateTo({ path: '/shows', query: year ? { year } : {} })
}
</script>
<style scoped>
.library-filters {
  display: grid;
  gap: 14px;
  min-width: 0;
}
.search-form {
  display: grid;
  gap: 8px;
}
.search-form > label {
  position: absolute;
  width: 1px;
  height: 1px;
  padding: 0;
  margin: -1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
.search-row {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
}
input,
select {
  min-width: 0;
  padding: 11px 15px;
  border: 1px solid var(--base-color-text-muted);
  border-radius: 16px;
  background: white;
  color: var(--base-color-text-primary);
  font: inherit;
}
button {
  padding: 10px 20px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-brand-red);
  color: black;
  cursor: pointer;
}
.filter-row,
.status-filters {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
}
.status-filters a {
  padding: 10px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
}
.status-filters a[aria-current] {
  background: var(--base-color-text-primary);
  color: white;
}
.year-filter {
  display: flex;
  gap: 10px;
  align-items: center;
  color: var(--base-color-text-secondary);
}
.filter-note {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.85rem;
}
.clear-filter {
  font-size: 0.9rem;
  text-decoration: underline;
}
@media (max-width: 600px) {
  .year-filter {
    flex-wrap: wrap;
  }
  .filter-row {
    gap: 16px;
  }
}
</style>
