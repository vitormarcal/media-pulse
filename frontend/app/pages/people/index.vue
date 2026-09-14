<template>
  <main class="people-page">
    <section class="people-hero">
      <div>
        <p class="eyebrow">Curadoria pessoal</p>
        <h1>Pessoas</h1>
        <p>Rostos e trajetórias que você quer reencontrar no seu arquivo audiovisual.</p>
      </div>
      <label class="search-box">
        <span>Buscar no catálogo local</span>
        <input v-model.trim="query" type="search" placeholder="Nome da pessoa" @input="scheduleSearch" />
      </label>
    </section>

    <section class="favorites-section">
      <div class="section-copy">
        <p class="eyebrow">Sua seleção</p>
        <h2>Favoritos</h2>
      </div>
      <div v-if="favorites?.length" class="people-grid">
        <article v-for="person in favorites" :key="person.personId" class="favorite-card">
          <NuxtLink :to="`/people/${person.slug}`" class="favorite-portrait">
            <img
              v-if="resolveMediaUrl(person.profileUrl)"
              :src="resolveMediaUrl(person.profileUrl)!"
              :alt="person.name"
            />
            <span v-else>{{ person.name.slice(0, 1) }}</span>
          </NuxtLink>
          <div class="favorite-copy">
            <NuxtLink :to="`/people/${person.slug}`"
              ><h3>{{ person.name }}</h3></NuxtLink
            >
            <p>{{ relationshipLabel(person) }}</p>
          </div>
          <button
            type="button"
            :aria-label="`Remover ${person.name} dos favoritos`"
            @click="removeFavorite(person.personId)"
          >
            ★
          </button>
        </article>
      </div>
      <div v-else class="empty-state">
        <p>Seus favoritos aparecerão aqui.</p>
        <small>Use a busca para começar uma seleção pessoal.</small>
      </div>
    </section>

    <section v-for="section in visibleHistorySections" :key="section.category" class="history-section">
      <div class="section-copy">
        <p class="eyebrow">No seu histórico</p>
        <h2>{{ section.title }}</h2>
      </div>
      <div class="history-grid">
        <article v-for="person in section.items" :key="person.personId" class="history-card">
          <NuxtLink :to="`/people/${person.slug}`" class="history-portrait">
            <img
              v-if="resolveMediaUrl(person.profileUrl)"
              :src="resolveMediaUrl(person.profileUrl)!"
              :alt="person.name"
            />
            <span v-else>{{ person.name.slice(0, 1) }}</span>
          </NuxtLink>
          <div class="history-copy">
            <NuxtLink :to="`/people/${person.slug}`"
              ><h3>{{ person.name }}</h3></NuxtLink
            >
            <p>{{ watchedWorksLabel(person.watchedWorksCount, section.workLabel) }}</p>
          </div>
          <button
            type="button"
            :aria-label="`Adicionar ${person.name} aos favoritos`"
            @click="favoriteFromHistory(person)"
          >
            ☆
          </button>
        </article>
      </div>
      <div v-if="section.nextOffset !== null" class="load-more-row">
        <button class="load-more" type="button" :disabled="section.loading" @click="loadMoreHistory(section)">
          {{ section.loading ? 'Carregando…' : 'Carregar mais' }}
        </button>
      </div>
    </section>

    <section v-if="query" class="results-section">
      <div class="section-copy">
        <p class="eyebrow">Busca local</p>
        <h2>{{ searching ? 'Buscando…' : 'Pessoas encontradas' }}</h2>
      </div>
      <div v-if="results.length" class="people-grid compact">
        <article v-for="person in results" :key="person.personId" class="person-card">
          <NuxtLink :to="`/people/${person.slug}`" class="portrait-link">
            <img
              v-if="resolveMediaUrl(person.profileUrl)"
              :src="resolveMediaUrl(person.profileUrl)!"
              :alt="person.name"
            />
            <span v-else>{{ person.name.slice(0, 1) }}</span>
          </NuxtLink>
          <div class="card-copy">
            <NuxtLink :to="`/people/${person.slug}`"
              ><strong>{{ person.name }}</strong></NuxtLink
            >
            <small>{{ person.roles.slice(0, 2).join(' · ') || 'Catálogo audiovisual' }}</small>
          </div>
          <button type="button" :aria-label="favoriteLabel(person)" @click="toggleSearchFavorite(person)">
            {{ person.favorite ? '★' : '☆' }}
          </button>
        </article>
      </div>
      <p v-else-if="!searching" class="empty-copy">Nenhuma pessoa local encontrada.</p>
    </section>
  </main>
</template>

<script setup lang="ts">
import type { PersonFavoriteDto, PersonSuggestionDto } from '~/types/movies'
import type { PersonHistoryCategory, PersonHistoryItemDto, PersonHistoryPageResponse } from '~/types/people'

interface HistorySectionState {
  category: PersonHistoryCategory
  title: string
  workLabel: string
  items: PersonHistoryItemDto[]
  nextOffset: number | null
  loading: boolean
}

const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const query = ref('')
const results = ref<PersonSuggestionDto[]>([])
const searching = ref(false)
const historySections = reactive<HistorySectionState[]>([
  { category: 'CAST', title: 'Elenco', workLabel: 'no elenco', items: [], nextOffset: null, loading: false },
  { category: 'DIRECTING', title: 'Direção', workLabel: 'na direção', items: [], nextOffset: null, loading: false },
  { category: 'WRITING', title: 'Roteiro', workLabel: 'no roteiro', items: [], nextOffset: null, loading: false },
])
let timer: ReturnType<typeof setTimeout> | undefined

const { data: favorites, refresh } = await useFetch<PersonFavoriteDto[]>('/api/people/favorites', {
  baseURL: config.public.apiBase,
  default: () => [],
})

const initialHistoryPages = await Promise.all(historySections.map((section) => fetchHistory(section.category, 0)))
initialHistoryPages.forEach((page, index) => {
  historySections[index]!.items = page.items
  historySections[index]!.nextOffset = page.nextOffset
})

const visibleHistorySections = computed(() => historySections.filter((section) => section.items.length))

function scheduleSearch() {
  clearTimeout(timer)
  if (!query.value) {
    results.value = []
    searching.value = false
    return
  }
  searching.value = true
  timer = setTimeout(runSearch, 180)
}

async function runSearch() {
  results.value = await $fetch<PersonSuggestionDto[]>('/api/people/search', {
    baseURL: config.public.apiBase,
    query: { q: query.value, limit: 12 },
  })
  searching.value = false
}

async function toggleSearchFavorite(person: PersonSuggestionDto) {
  await $fetch(`/api/people/${person.personId}/favorite`, {
    baseURL: config.public.apiBase,
    method: person.favorite ? 'DELETE' : 'POST',
  })
  person.favorite = !person.favorite
  if (person.favorite) {
    removeFromHistory(person.personId)
  } else {
    await reloadHistory()
  }
  await refresh()
}

async function removeFavorite(personId: number) {
  await $fetch(`/api/people/${personId}/favorite`, { baseURL: config.public.apiBase, method: 'DELETE' })
  const match = results.value.find((item) => item.personId === personId)
  if (match) match.favorite = false
  await refresh()
  await reloadHistory()
}

async function favoriteFromHistory(person: PersonHistoryItemDto) {
  await $fetch(`/api/people/${person.personId}/favorite`, {
    baseURL: config.public.apiBase,
    method: 'POST',
  })
  removeFromHistory(person.personId)
  const match = results.value.find((item) => item.personId === person.personId)
  if (match) match.favorite = true
  await refresh()
}

function removeFromHistory(personId: number) {
  historySections.forEach((section) => {
    const previousSize = section.items.length
    section.items = section.items.filter((item) => item.personId !== personId)
    if (section.items.length < previousSize && section.nextOffset !== null) {
      section.nextOffset = Math.max(0, section.nextOffset - 1)
    }
  })
}

async function loadMoreHistory(section: HistorySectionState) {
  if (section.nextOffset === null || section.loading) return
  section.loading = true
  try {
    const page = await fetchHistory(section.category, section.nextOffset)
    section.items.push(...page.items)
    section.nextOffset = page.nextOffset
  } finally {
    section.loading = false
  }
}

async function reloadHistory() {
  const pages = await Promise.all(historySections.map((section) => fetchHistory(section.category, 0)))
  pages.forEach((page, index) => {
    historySections[index]!.items = page.items
    historySections[index]!.nextOffset = page.nextOffset
  })
}

function fetchHistory(category: PersonHistoryCategory, offset: number) {
  return $fetch<PersonHistoryPageResponse>('/api/people/history/most-present', {
    baseURL: config.public.apiBase,
    query: { category, limit: 4, offset },
  })
}

function watchedWorksLabel(count: number, workLabel: string) {
  return `${count} ${count === 1 ? 'obra' : 'obras'} ${workLabel}`
}

function favoriteLabel(person: PersonSuggestionDto) {
  return `${person.favorite ? 'Remover' : 'Adicionar'} ${person.name} ${person.favorite ? 'dos' : 'aos'} favoritos`
}

function relationshipLabel(person: PersonFavoriteDto) {
  return `${person.watchedMoviesCount} filmes · ${person.watchedShowsCount} séries`
}

useHead({ title: 'Pessoas · Media Pulse' })
</script>

<style scoped>
.people-page {
  display: grid;
  gap: 64px;
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}
.people-hero {
  display: grid;
  grid-template-columns: 1fr minmax(260px, 420px);
  gap: 32px;
  align-items: end;
  padding: clamp(28px, 5vw, 56px);
  border-radius: 40px;
  background: radial-gradient(circle at top right, rgba(230, 0, 35, 0.1), transparent 32%), #f6f3ee;
}
.eyebrow {
  margin: 0 0 8px;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}
h1 {
  margin: 0;
  font-size: clamp(3.6rem, 9vw, 7rem);
  line-height: 0.9;
  letter-spacing: -0.07em;
}
.people-hero p:last-child,
.favorite-copy p {
  color: var(--base-color-text-secondary);
}
.search-box {
  display: grid;
  gap: 8px;
  font-size: 0.78rem;
  font-weight: 700;
}
.search-box input {
  padding: 14px 16px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
  font: inherit;
}
.results-section,
.favorites-section,
.history-section {
  display: grid;
  gap: 24px;
}
.history-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 20px;
}
.history-card {
  position: relative;
  display: grid;
  align-content: start;
  gap: 12px;
}
.history-portrait {
  display: grid;
  aspect-ratio: 3 / 4;
  overflow: hidden;
  border-radius: 24px;
  background: var(--base-color-surface-warm);
  place-items: center;
  font-size: 4rem;
}
.history-portrait img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.history-copy {
  display: grid;
  gap: 4px;
}
.history-copy h3,
.history-copy p {
  margin: 0;
}
.history-copy p {
  color: var(--base-color-text-secondary);
}
.history-card > button {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 38px;
  height: 38px;
  border: 0;
  border-radius: 50%;
  background: var(--base-color-brand-red);
  color: white;
  cursor: pointer;
  font-size: 1rem;
}
.load-more-row {
  display: flex;
  justify-content: center;
}
.load-more {
  padding: 10px 18px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  cursor: pointer;
}
.load-more:disabled {
  cursor: default;
  opacity: 0.7;
}
.section-copy h2 {
  margin: 0;
  font-size: 1.75rem;
}
.people-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(210px, 1fr));
  gap: 20px;
}
.favorite-card {
  position: relative;
  display: grid;
  gap: 12px;
}
.favorite-portrait {
  display: grid;
  aspect-ratio: 3/4;
  overflow: hidden;
  border-radius: 24px;
  background: var(--base-color-surface-warm);
  place-items: center;
  font-size: 4rem;
}
.favorite-portrait img,
.portrait-link img {
  width: 100%;
  height: 100%;
  object-fit: cover;
}
.favorite-copy h3,
.favorite-copy p {
  margin: 0;
}
.favorite-copy {
  display: grid;
  gap: 4px;
}
.favorite-card button,
.person-card button {
  border: 0;
  border-radius: 50%;
  background: var(--base-color-brand-red);
  color: white;
  cursor: pointer;
  font-size: 1rem;
}
.favorite-card button {
  position: absolute;
  top: 12px;
  right: 12px;
  width: 38px;
  height: 38px;
}
.people-grid.compact {
  grid-template-columns: repeat(auto-fill, minmax(280px, 1fr));
}
.person-card {
  display: grid;
  grid-template-columns: 64px 1fr 36px;
  gap: 12px;
  align-items: center;
  padding: 10px;
  border-radius: 20px;
  background: #f6f3ee;
}
.portrait-link {
  display: grid;
  width: 64px;
  height: 64px;
  overflow: hidden;
  border-radius: 50%;
  background: var(--base-color-surface-warm);
  place-items: center;
}
.person-card button {
  width: 36px;
  height: 36px;
}
.card-copy {
  display: grid;
  gap: 3px;
  min-width: 0;
}
.card-copy small {
  overflow: hidden;
  color: var(--base-color-text-secondary);
  text-overflow: ellipsis;
  white-space: nowrap;
}
.empty-state {
  padding: 32px;
  border-radius: 24px;
  background: #f6f3ee;
}
.empty-state p,
.empty-state small,
.empty-copy {
  margin: 0;
}
@media (max-width: 760px) {
  .people-hero {
    grid-template-columns: 1fr;
  }
  .people-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
  .people-grid.compact {
    grid-template-columns: 1fr;
  }
  .history-grid {
    grid-template-columns: repeat(2, minmax(0, 1fr));
  }
}

@media (max-width: 575px) {
  .history-grid {
    grid-template-columns: 1fr;
  }
}

@media (min-width: 761px) and (max-width: 1100px) {
  .history-grid {
    grid-template-columns: repeat(3, minmax(0, 1fr));
  }
}
</style>
