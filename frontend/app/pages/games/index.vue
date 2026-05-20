<template>
  <main class="games-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página de games...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível montar a página de games com os dados atuais.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <section class="games-hero" :style="heroStyle">
        <div class="hero-copy">
          <p class="eyebrow">Games</p>
          <h1>{{ data.hero.title }}</h1>
          <p v-if="data.hero.intro">{{ data.hero.intro }}</p>
          <div class="hero-actions">
            <NuxtLink class="primary-link" :to="data.hero.accentLink">{{ data.hero.accentLabel }}</NuxtLink>
            <NuxtLink v-if="queryText || addMode" class="secondary-link" to="/games">Limpar recorte</NuxtLink>
          </div>
        </div>

        <NuxtLink v-if="data.hero.spotlight" class="spotlight" :to="data.hero.spotlight.href">
          <img
            v-if="resolveMediaUrl(data.hero.spotlight.imageUrl)"
            :src="resolveMediaUrl(data.hero.spotlight.imageUrl)"
            :alt="data.hero.spotlight.title"
          />
          <div v-else class="spotlight-fallback">Sem imagem</div>
          <div>
            <strong>{{ data.hero.spotlight.title }}</strong>
            <span>{{ data.hero.spotlight.subtitle }}</span>
          </div>
        </NuxtLink>
      </section>

      <section class="filters">
        <form class="search-form" @submit.prevent="applySearch">
          <label>
            <span>Buscar game</span>
            <input v-model="draftQuery" type="search" placeholder="Nome do jogo" />
          </label>
          <button type="submit">Buscar</button>
        </form>
      </section>

      <GameManualAddCard v-if="addMode" :initial-title="queryText" />

      <section class="stats-grid">
        <article v-for="metric in data.stats" :key="metric.id" class="stat-card">
          <p>{{ metric.label }}</p>
          <strong>{{ metric.value }}</strong>
          <span>{{ metric.note }}</span>
        </article>
      </section>

      <section class="library-section">
        <SectionHeading
          eyebrow="Arquivo"
          :title="data.mode === 'search' ? 'O que respondeu à busca' : 'A parede completa de games'"
        />

        <div v-if="displayItems.length" class="games-grid">
          <NuxtLink v-for="item in displayItems" :key="item.id" class="game-card" :to="item.href">
            <img v-if="resolveMediaUrl(item.imageUrl)" :src="resolveMediaUrl(item.imageUrl)" :alt="item.title" />
            <div v-else class="game-fallback">Sem imagem</div>
            <strong>{{ item.title }}</strong>
            <span>{{ item.subtitle }}</span>
            <p>{{ item.meta }}</p>
          </NuxtLink>
        </div>

        <article v-else class="state-card">
          <p>Nenhum game encontrado neste recorte.</p>
        </article>
      </section>

      <div v-if="canLoadMore" class="load-more-row">
        <button type="button" class="load-more" :disabled="loadingMore" @click="handleLoadMore">
          {{ loadingMore ? 'Buscando mais games...' : 'Carregar mais games' }}
        </button>
      </div>
    </template>
  </main>
</template>

<script setup lang="ts">
import GameManualAddCard from '~/components/games/GameManualAddCard.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'
import { fetchGamesLibraryNextPage, fetchGamesLibraryPageData } from '~/composables/useGamesLibraryData'
import type { GameLibraryCardModel } from '~/types/games'
import { buildGameLibraryCard } from '~/utils/games'

const route = useRoute()
const router = useRouter()
const { resolveMediaUrl } = useMediaUrl()

const queryText = computed(() => {
  const value = route.query.q
  return typeof value === 'string' ? value.trim() : ''
})
const addMode = computed(() => route.query.add === '1' || route.query.add === 'true')
const draftQuery = ref(queryText.value)

const { data, error, status } = await useAsyncData(
  'games-library-page',
  () => fetchGamesLibraryPageData(queryText.value),
  { watch: [queryText] },
)

const nextCursor = ref<string | null>(null)
const extraItems = ref<GameLibraryCardModel[]>([])
const loadingMore = ref(false)

watch(
  data,
  (value) => {
    nextCursor.value = value?.libraryCursor ?? null
    extraItems.value = []
  },
  { immediate: true },
)

watch(queryText, (value) => {
  draftQuery.value = value
})

const displayItems = computed(() => [...(data.value?.items ?? []), ...extraItems.value])
const canLoadMore = computed(() => data.value?.mode === 'library' && !!nextCursor.value)
const heroStyle = computed(() => {
  const imageUrl = resolveMediaUrl(data.value?.hero.spotlight?.imageUrl ?? null)
  return imageUrl
    ? {
        backgroundImage: `linear-gradient(180deg, rgba(255,255,255,0.86), rgba(246,243,238,0.97)), url("${imageUrl}")`,
      }
    : undefined
})

async function applySearch() {
  const q = draftQuery.value.trim()
  await router.push(q ? `/games?q=${encodeURIComponent(q)}` : '/games')
}

async function handleLoadMore() {
  if (!nextCursor.value) return
  loadingMore.value = true
  try {
    const response = await fetchGamesLibraryNextPage(nextCursor.value)
    extraItems.value = [...extraItems.value, ...response.items.map(buildGameLibraryCard)]
    nextCursor.value = response.nextCursor
  } finally {
    loadingMore.value = false
  }
}

useHead({ title: 'Games · Media Pulse' })
</script>

<style scoped>
.games-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.games-hero {
  display: grid;
  grid-template-columns: minmax(0, 1fr) minmax(18rem, 25rem);
  gap: 28px;
  align-items: end;
  min-height: 28rem;
  padding: clamp(24px, 5vw, 54px);
  border-radius: 40px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.98));
  background-size: cover;
  background-position: center;
}

.hero-copy {
  display: grid;
  gap: 14px;
}

.eyebrow {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

h1 {
  margin: 0;
  font-size: clamp(3.2rem, 8vw, 6.4rem);
  line-height: 0.9;
  letter-spacing: 0;
}

.hero-copy p {
  max-width: 48rem;
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.6;
}

.hero-actions {
  display: flex;
  gap: 10px;
  flex-wrap: wrap;
}

.primary-link,
.secondary-link,
.search-form button,
.load-more {
  border: 0;
  border-radius: 16px;
  padding: 12px 16px;
  font: inherit;
  cursor: pointer;
}

.primary-link {
  background: var(--base-color-brand-red);
  color: #000000;
}

.secondary-link,
.search-form button,
.load-more {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.spotlight {
  display: grid;
  gap: 12px;
  padding: 12px;
  border-radius: 28px;
  background: rgba(255, 255, 255, 0.72);
  color: var(--base-color-text-primary);
}

.spotlight img,
.spotlight-fallback {
  width: 100%;
  aspect-ratio: 2 / 3;
  border-radius: 20px;
  object-fit: cover;
  background: var(--base-color-surface-warm);
}

.spotlight-fallback,
.game-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
}

.spotlight span,
.game-card span,
.game-card p,
.stat-card p,
.stat-card span {
  color: var(--base-color-text-secondary);
}

.filters,
.state-card {
  padding: 24px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.search-form {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
  align-items: end;
}

.search-form label {
  display: grid;
  gap: 8px;
}

.search-form span {
  color: var(--base-color-text-secondary);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.search-form input {
  padding: 12px 14px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
}

.stats-grid {
  display: grid;
  grid-template-columns: repeat(4, minmax(0, 1fr));
  gap: 14px;
}

.stat-card {
  display: grid;
  gap: 8px;
  padding: 18px;
  border-radius: 24px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 84%, var(--base-color-surface-soft));
}

.stat-card p,
.stat-card span,
.game-card p {
  margin: 0;
}

.stat-card strong {
  font-size: 1.8rem;
}

.library-section {
  display: grid;
  gap: 24px;
}

.games-grid {
  columns: 6 180px;
  column-gap: 18px;
}

.game-card {
  display: grid;
  break-inside: avoid;
  gap: 8px;
  margin: 0 0 18px;
  color: var(--base-color-text-primary);
}

.game-card img,
.game-fallback {
  width: 100%;
  aspect-ratio: 2 / 3;
  border-radius: 20px;
  object-fit: cover;
  background: var(--base-color-surface-warm);
}

.load-more-row {
  display: flex;
  justify-content: center;
}

.state-card.error {
  color: #7a1414;
}

pre {
  white-space: pre-wrap;
}

@media (max-width: 900px) {
  .games-page {
    width: min(100vw - 20px, 1480px);
  }

  .games-hero,
  .search-form,
  .stats-grid {
    grid-template-columns: 1fr;
  }
}
</style>
