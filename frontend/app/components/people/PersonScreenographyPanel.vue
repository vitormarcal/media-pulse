<template>
  <section class="screenography-panel">
    <SectionHeading
      eyebrow="Filmografia TMDb"
      title="Expandir a presença desta pessoa"
      description="Cruze filmes e séries do TMDb com o catálogo local e traga o que ainda estiver faltando."
      :summary="panelSummary"
    />

    <div class="toolbar-card">
      <div class="mode-switch" role="tablist" aria-label="Recorte da filmografia">
        <button
          v-for="mode in modes"
          :key="mode.id"
          type="button"
          class="mode-button"
          :class="{ active: activeKind === mode.id }"
          @click="activeKind = mode.id"
        >
          <span>{{ mode.label }}</span>
          <small>{{ mode.countLabel }}</small>
        </button>
      </div>

      <div class="toolbar-meta">
        <p class="count-copy">{{ countLabel }}</p>
        <button v-if="showLoadButton" type="button" class="load-button" :disabled="currentLoading" @click="loadActive">
          {{ currentLoading ? 'Carregando...' : loadButtonLabel }}
        </button>
      </div>
    </div>

    <p v-if="currentError" class="panel-error">{{ currentError }}</p>

    <div v-if="currentMembers.length" class="cards-grid">
      <article v-for="item in currentMembers" :key="`${activeKind}:${item.tmdbId}`" class="filmography-card">
        <component
          :is="item.localSlug ? NuxtLink : 'div'"
          class="poster-link"
          :to="item.localSlug ? `${item.kind === 'movies' ? '/movies' : '/shows'}/${item.localSlug}` : undefined"
        >
          <div class="poster-shell">
            <img v-if="resolveMediaUrl(item.posterUrl)" :src="resolveMediaUrl(item.posterUrl)" :alt="item.title" />
            <div v-else class="poster-fallback">{{ item.title.slice(0, 1) }}</div>
          </div>
        </component>

        <div class="card-copy">
          <span class="card-tag">{{ item.inCatalog ? 'Catálogo' : 'Sugestão TMDb' }}</span>
          <strong>{{ item.title }}</strong>
          <p class="card-meta">{{ item.year ? String(item.year) : 'Sem ano' }}</p>
          <p class="card-meta">{{ item.roleLabel }}</p>
        </div>

        <div class="card-actions">
          <a v-if="item.tmdbUrl" class="tmdb-link" :href="item.tmdbUrl" target="_blank" rel="noreferrer">TMDb</a>
          <button
            v-if="!item.inCatalog"
            type="button"
            class="add-button"
            :disabled="addingKey === `${item.kind}:${item.tmdbId}`"
            @click="addMember(item)"
          >
            {{ addingKey === `${item.kind}:${item.tmdbId}` ? 'Adicionando...' : 'Adicionar' }}
          </button>
        </div>
      </article>
    </div>

    <div v-else-if="currentLoaded" class="empty-card">
      <p>Nenhum item apareceu nesse recorte do TMDb.</p>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type {
  ManualMovieCatalogCreateResponse,
  PersonFilmographyResponse,
  PersonPageData,
  PersonShowFilmographyResponse,
} from '~/types/movies'
import type { ManualShowCatalogCreateResponse } from '~/types/shows'

type ScreenographyKind = 'movies' | 'shows'

type ScreenographyMemberViewModel = {
  kind: ScreenographyKind
  tmdbId: string
  title: string
  year: number | null
  posterUrl: string | null
  tmdbUrl: string
  localSlug: string | null
  inCatalog: boolean
  roleLabel: string
}

const NuxtLink = resolveComponent('NuxtLink')

const props = defineProps<{
  person: PersonPageData
}>()

const emit = defineEmits<{
  added: [response: ManualMovieCatalogCreateResponse | ManualShowCatalogCreateResponse]
}>()

const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const activeKind = ref<ScreenographyKind>('movies')
const movieFilmography = ref<PersonFilmographyResponse | null>(null)
const showFilmography = ref<PersonShowFilmographyResponse | null>(null)
const movieLoading = ref(false)
const showLoading = ref(false)
const movieError = ref<string | null>(null)
const showError = ref<string | null>(null)
const addingKey = ref<string | null>(null)

const modes = computed(() => [
  {
    id: 'movies' as const,
    label: 'Filmes',
    countLabel: `${props.person.stats.movieCount} locais`,
  },
  {
    id: 'shows' as const,
    label: 'Séries',
    countLabel: `${props.person.stats.showCount} locais`,
  },
])

const currentLoading = computed(() => (activeKind.value === 'movies' ? movieLoading.value : showLoading.value))
const currentError = computed(() => (activeKind.value === 'movies' ? movieError.value : showError.value))
const currentLoaded = computed(() =>
  activeKind.value === 'movies' ? !!movieFilmography.value : !!showFilmography.value,
)

const currentMembers = computed<ScreenographyMemberViewModel[]>(() => {
  if (activeKind.value === 'movies') {
    return (
      movieFilmography.value?.members.map((item) => ({
        kind: 'movies' as const,
        tmdbId: item.tmdbId,
        title: item.title,
        year: item.year,
        posterUrl: item.posterUrl,
        tmdbUrl: item.tmdbUrl,
        localSlug: item.localSlug,
        inCatalog: item.inCatalog,
        roleLabel: item.roleLabel,
      })) ?? []
    )
  }

  return (
    showFilmography.value?.members.map((item) => ({
      kind: 'shows' as const,
      tmdbId: item.tmdbId,
      title: item.title,
      year: item.year,
      posterUrl: item.posterUrl,
      tmdbUrl: item.tmdbUrl,
      localSlug: item.localSlug,
      inCatalog: item.inCatalog,
      roleLabel: item.roleLabel,
    })) ?? []
  )
})

const showLoadButton = computed(() => !currentLoaded.value || currentError.value != null)
const loadButtonLabel = computed(() => {
  if (currentError.value) return 'Tentar novamente'
  return activeKind.value === 'movies' ? 'Ver filmes' : 'Ver séries'
})

const countLabel = computed(() => {
  if (activeKind.value === 'movies') {
    if (!movieFilmography.value) return `${props.person.stats.movieCount} filmes locais ligados a esta pessoa`
    const catalogued = movieFilmography.value.members.filter((item) => item.inCatalog).length
    return movieFilmography.value.members.length
      ? `${catalogued}/${movieFilmography.value.members.length} filmes já estão no catálogo`
      : 'Nenhum filme retornado pelo TMDb'
  }

  if (!showFilmography.value) return `${props.person.stats.showCount} séries locais ligadas a esta pessoa`
  const catalogued = showFilmography.value.members.filter((item) => item.inCatalog).length
  return showFilmography.value.members.length
    ? `${catalogued}/${showFilmography.value.members.length} séries já estão no catálogo`
    : 'Nenhuma série retornada pelo TMDb'
})

const panelSummary = computed(() => {
  const movieLoaded = !!movieFilmography.value
  const showLoaded = !!showFilmography.value

  if (!movieLoaded && !showLoaded) {
    return `${props.person.stats.movieCount} filmes locais e ${props.person.stats.showCount} séries locais já ligados a esta pessoa.`
  }

  const movieSummary = movieLoaded
    ? `${movieFilmography.value!.members.filter((item) => item.inCatalog).length}/${movieFilmography.value!.members.length} filmes`
    : null
  const showSummary = showLoaded
    ? `${showFilmography.value!.members.filter((item) => item.inCatalog).length}/${showFilmography.value!.members.length} séries`
    : null

  return [movieSummary, showSummary].filter(Boolean).join(' · ')
})

async function loadActive() {
  if (activeKind.value === 'movies') {
    await loadMovies()
    return
  }

  await loadShows()
}

async function loadMovies() {
  if (movieLoading.value) return

  movieLoading.value = true
  movieError.value = null

  try {
    movieFilmography.value = await $fetch<PersonFilmographyResponse>(
      `/api/people/${props.person.personId}/tmdb-filmography`,
      {
        baseURL: config.public.apiBase,
      },
    )
  } catch {
    movieError.value = 'Não foi possível carregar a filmografia de filmes desta pessoa.'
  } finally {
    movieLoading.value = false
  }
}

async function loadShows() {
  if (showLoading.value) return

  showLoading.value = true
  showError.value = null

  try {
    showFilmography.value = await $fetch<PersonShowFilmographyResponse>(
      `/api/people/${props.person.personId}/tmdb-show-filmography`,
      {
        baseURL: config.public.apiBase,
      },
    )
  } catch {
    showError.value = 'Não foi possível carregar a filmografia de séries desta pessoa.'
  } finally {
    showLoading.value = false
  }
}

async function addMember(item: ScreenographyMemberViewModel) {
  const key = `${item.kind}:${item.tmdbId}`
  if (addingKey.value) return

  addingKey.value = key
  movieError.value = null
  showError.value = null

  try {
    if (item.kind === 'movies') {
      const response = await $fetch<ManualMovieCatalogCreateResponse>('/api/movies/catalog', {
        baseURL: config.public.apiBase,
        method: 'POST',
        body: {
          title: item.title,
          year: item.year,
          tmdbId: item.tmdbId,
          imdbId: null,
        },
      })

      const member = movieFilmography.value?.members.find((candidate) => candidate.tmdbId === item.tmdbId)
      if (member) {
        member.inCatalog = true
        member.localMovieId = response.movieId
        member.localSlug = response.slug
      }
      emit('added', response)
      return
    }

    const response = await $fetch<ManualShowCatalogCreateResponse>('/api/shows/catalog', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: {
        title: item.title,
        year: item.year,
        tmdbId: item.tmdbId,
        importEpisodes: true,
      },
    })

    const member = showFilmography.value?.members.find((candidate) => candidate.tmdbId === item.tmdbId)
    if (member) {
      member.inCatalog = true
      member.localShowId = response.showId
      member.localSlug = response.slug
    }
    emit('added', response)
  } catch {
    if (item.kind === 'movies') {
      movieError.value = `Não foi possível adicionar "${item.title}".`
    } else {
      showError.value = `Não foi possível adicionar "${item.title}".`
    }
  } finally {
    addingKey.value = null
  }
}
</script>

<style scoped>
.screenography-panel {
  display: grid;
  gap: 22px;
}

.toolbar-card {
  display: grid;
  gap: 16px;
  padding: 18px 20px;
  border-radius: 28px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 82%, white);
}

.mode-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.mode-button,
.load-button,
.add-button {
  border: none;
  font: inherit;
  cursor: pointer;
}

.mode-button {
  display: grid;
  gap: 2px;
  min-width: 10.5rem;
  padding: 10px 14px;
  border-radius: 18px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 70%, white);
  color: var(--base-color-text-primary);
  text-align: left;
}

.mode-button span {
  font-weight: 700;
}

.mode-button small {
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
}

.mode-button.active {
  background: var(--base-color-surface-warm);
}

.toolbar-meta {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.count-copy,
.panel-error,
.card-meta,
.empty-card p {
  margin: 0;
}

.count-copy {
  color: var(--base-color-text-secondary);
}

.load-button,
.add-button {
  padding: 8px 14px;
  border-radius: 16px;
}

.load-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.panel-error {
  color: #7a1414;
}

.cards-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(170px, 1fr));
  gap: 16px;
}

.filmography-card {
  display: grid;
  gap: 12px;
  align-content: start;
  padding: 14px;
  border-radius: 24px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 78%, white);
}

.poster-link {
  color: inherit;
}

.poster-shell {
  overflow: hidden;
  aspect-ratio: 0.68;
  border-radius: 20px;
  background: var(--base-color-surface-soft);
}

.poster-shell img,
.poster-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.poster-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-primary);
  font-size: 3rem;
  font-weight: 700;
}

.card-copy {
  display: grid;
  gap: 4px;
}

.card-tag {
  color: var(--base-color-brand-red);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.card-copy strong {
  font-size: 1rem;
  line-height: 1.08;
}

.card-meta {
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
}

.card-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.tmdb-link {
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
}

.add-button {
  background: color-mix(in srgb, var(--base-color-brand-red) 12%, white);
  color: var(--base-color-text-primary);
}

.empty-card {
  padding: 18px 20px;
  border-radius: 24px;
  background: color-mix(in srgb, var(--base-color-surface-strong) 74%, white);
  color: var(--base-color-text-secondary);
}

@media (max-width: 720px) {
  .toolbar-meta {
    align-items: start;
    flex-direction: column;
  }

  .mode-button {
    min-width: 0;
    flex: 1 1 12rem;
  }
}
</style>
