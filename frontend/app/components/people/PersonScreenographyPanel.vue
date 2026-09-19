<template>
  <section class="screenography-panel">
    <SectionHeading
      eyebrow="Filmografia audiovisual"
      title="Explorar filmes e séries"
      :description="panelDescription"
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
          role="tab"
          :aria-selected="activeKind === mode.id"
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

    <div v-if="currentCompacted" class="compacted-notice">
      <p><strong>Vínculos locais pendentes</strong> — atualize para consultar a filmografia completa.</p>
      <button type="button" :disabled="currentLoading" @click="refreshActive">
        {{ currentLoading ? 'Atualizando...' : 'Atualizar filmografia' }}
      </button>
    </div>

    <p v-if="currentError" class="panel-error">{{ currentError }}</p>

    <div v-if="currentMembers.length" class="member-groups">
      <section v-for="group in currentGroups" :key="group.id" class="member-group">
        <h3>{{ group.label }}</h3>
        <div class="cards-grid">
          <article v-for="item in group.items" :key="`${activeKind}:${item.tmdbId}`" class="filmography-card">
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
              <span class="card-tag">{{ statusLabel(item.watchStatus) }}</span>
              <strong>{{ item.title }}</strong>
              <p class="card-meta">{{ item.year ? String(item.year) : 'Sem ano' }}</p>
              <p class="card-meta">{{ item.roleLabel }}</p>
            </div>

            <div class="card-actions">
              <a v-if="item.tmdbUrl" class="tmdb-link" :href="item.tmdbUrl" target="_blank" rel="noreferrer">TMDb</a>
              <button
                v-if="item.inCatalog && item.availableCategories.length === 1"
                type="button"
                class="link-button"
                :disabled="linkingKey === `${item.kind}:${item.tmdbId}`"
                @click="linkMember(item, item.availableCategories[0]!)"
              >
                {{ linkingKey === `${item.kind}:${item.tmdbId}` ? 'Vinculando...' : 'Vincular' }}
              </button>
              <button
                v-else-if="item.inCatalog && item.availableCategories.length > 1"
                type="button"
                class="link-button"
                @click="toggleCategoryChoice(item)"
              >
                Vincular
              </button>
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
            <div v-if="categoryChoiceKey === `${item.kind}:${item.tmdbId}`" class="category-choice">
              <button
                v-for="category in item.availableCategories"
                :key="category"
                type="button"
                :disabled="linkingKey === `${item.kind}:${item.tmdbId}`"
                @click="linkMember(item, category)"
              >
                {{ categoryLabel(category) }}
              </button>
            </div>
            <p v-if="linkedFeedbackKey === `${item.kind}:${item.tmdbId}`" class="linked-feedback" role="status">
              Vinculado
            </p>
          </article>
        </div>
      </section>
    </div>

    <div v-else-if="currentLoaded" class="empty-card">
      <p>{{ emptyLabel }}</p>
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
  watchStatus: string
  linkedCategories: string[]
  availableCategories: string[]
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
const linkingKey = ref<string | null>(null)
const categoryChoiceKey = ref<string | null>(null)
const linkedFeedbackKey = ref<string | null>(null)

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
const currentCompacted = computed(() =>
  activeKind.value === 'movies'
    ? movieFilmography.value?.compacted === true
    : showFilmography.value?.compacted === true,
)
const panelDescription = computed(() =>
  currentCompacted.value
    ? 'Revise obras locais que ainda não estão completamente vinculadas a esta pessoa.'
    : 'Compare a filmografia do TMDb com o catálogo local e escolha o que deseja adicionar.',
)
const emptyLabel = computed(() =>
  currentCompacted.value
    ? 'Nenhum vínculo local pendente para esta pessoa.'
    : 'Nenhum item apareceu nesse recorte do TMDb.',
)

const currentMembers = computed<ScreenographyMemberViewModel[]>(() => {
  if (activeKind.value === 'movies') {
    return (
      movieFilmography.value?.members
        .map((item) => ({
          kind: 'movies' as const,
          tmdbId: item.tmdbId,
          title: item.title,
          year: item.year,
          posterUrl: item.posterUrl,
          tmdbUrl: item.tmdbUrl,
          localSlug: item.localSlug,
          inCatalog: item.inCatalog,
          roleLabel: item.roleLabel,
          watchStatus: item.watchStatus,
          linkedCategories: item.linkedCategories,
          availableCategories: item.availableCategories,
        }))
        .sort(compareWatchStatus) ?? []
    )
  }

  return (
    showFilmography.value?.members
      .map((item) => ({
        kind: 'shows' as const,
        tmdbId: item.tmdbId,
        title: item.title,
        year: item.year,
        posterUrl: item.posterUrl,
        tmdbUrl: item.tmdbUrl,
        localSlug: item.localSlug,
        inCatalog: item.inCatalog,
        roleLabel: item.roleLabel,
        watchStatus: item.watchStatus,
        linkedCategories: item.linkedCategories,
        availableCategories: item.availableCategories,
      }))
      .sort(compareWatchStatus) ?? []
  )
})

function compareWatchStatus(a: ScreenographyMemberViewModel, b: ScreenographyMemberViewModel) {
  const order = ['UNWATCHED', 'NOT_STARTED', 'IN_PROGRESS', 'OUTSIDE_CATALOG', 'WATCHED']
  return order.indexOf(a.watchStatus) - order.indexOf(b.watchStatus)
}

function statusLabel(status: string) {
  return (
    {
      UNWATCHED: 'No catálogo · ainda não assistido',
      NOT_STARTED: 'No catálogo · não iniciada',
      IN_PROGRESS: 'No catálogo · em andamento',
      OUTSIDE_CATALOG: 'Fora do catálogo',
      WATCHED: 'Assistido',
    }[status] ?? 'Filmografia'
  )
}

const currentGroups = computed(() => {
  const labels: Record<string, string> = {
    UNWATCHED: 'No catálogo, ainda não assistidos',
    NOT_STARTED: 'Não iniciadas',
    IN_PROGRESS: 'Em andamento',
    OUTSIDE_CATALOG: 'Fora do catálogo',
    WATCHED: 'Assistidos',
  }
  return ['UNWATCHED', 'NOT_STARTED', 'IN_PROGRESS', 'OUTSIDE_CATALOG', 'WATCHED']
    .map((id) => ({ id, label: labels[id], items: currentMembers.value.filter((item) => item.watchStatus === id) }))
    .filter((group) => group.items.length)
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
    if (movieFilmography.value.compacted) {
      return movieFilmography.value.members.length
        ? `${movieFilmography.value.members.length} filmes locais com vínculos pendentes`
        : 'Nenhum vínculo de filme pendente'
    }
    return movieFilmography.value.members.length
      ? `${catalogued}/${movieFilmography.value.members.length} filmes já estão no catálogo`
      : 'Nenhum filme retornado pelo TMDb'
  }

  if (!showFilmography.value) return `${props.person.stats.showCount} séries locais ligadas a esta pessoa`
  const catalogued = showFilmography.value.members.filter((item) => item.inCatalog).length
  if (showFilmography.value.compacted) {
    return showFilmography.value.members.length
      ? `${showFilmography.value.members.length} séries locais com vínculos pendentes`
      : 'Nenhum vínculo de série pendente'
  }
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

async function refreshActive() {
  if (currentLoading.value) return
  if (activeKind.value === 'movies') {
    movieLoading.value = true
    movieError.value = null
    try {
      movieFilmography.value = await $fetch<PersonFilmographyResponse>(
        `/api/admin/people/${props.person.personId}/tmdb-filmography`,
        { baseURL: config.public.apiBase, method: 'POST' },
      )
    } catch {
      movieError.value = 'Não foi possível atualizar a filmografia de filmes desta pessoa.'
    } finally {
      movieLoading.value = false
    }
    return
  }

  showLoading.value = true
  showError.value = null
  try {
    showFilmography.value = await $fetch<PersonShowFilmographyResponse>(
      `/api/admin/people/${props.person.personId}/tmdb-show-filmography`,
      { baseURL: config.public.apiBase, method: 'POST' },
    )
  } catch {
    showError.value = 'Não foi possível atualizar a filmografia de séries desta pessoa.'
  } finally {
    showLoading.value = false
  }
}

async function loadMovies() {
  if (movieLoading.value) return

  movieLoading.value = true
  movieError.value = null

  try {
    movieFilmography.value = await $fetch<PersonFilmographyResponse>(
      `/api/people/${props.person.personId}/filmography`,
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
      `/api/people/${props.person.personId}/show-filmography`,
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

function toggleCategoryChoice(item: ScreenographyMemberViewModel) {
  const key = `${item.kind}:${item.tmdbId}`
  categoryChoiceKey.value = categoryChoiceKey.value === key ? null : key
}

function categoryLabel(category: string) {
  return { CAST: 'Elenco', DIRECTING: 'Direção', WRITING: 'Roteiro' }[category] ?? category
}

async function linkMember(item: ScreenographyMemberViewModel, category: string) {
  const localId =
    item.kind === 'movies'
      ? movieFilmography.value?.members.find((member) => member.tmdbId === item.tmdbId)?.localMovieId
      : showFilmography.value?.members.find((member) => member.tmdbId === item.tmdbId)?.localShowId
  if (!localId) return
  const key = `${item.kind}:${item.tmdbId}`
  linkingKey.value = key
  try {
    await $fetch(`/api/people/${props.person.personId}/filmography/${item.kind}/${localId}/link`, {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: { category },
    })
    item.availableCategories = item.availableCategories.filter((candidate) => candidate !== category)
    item.linkedCategories = [...item.linkedCategories, category]
    const source = item.kind === 'movies' ? movieFilmography.value?.members : showFilmography.value?.members
    const member = source?.find((candidate) => candidate.tmdbId === item.tmdbId)
    if (member) {
      member.availableCategories = member.availableCategories.filter((candidate) => candidate !== category)
      member.linkedCategories = [...member.linkedCategories, category]
      const filmography = item.kind === 'movies' ? movieFilmography.value : showFilmography.value
      if (filmography?.compacted && !member.availableCategories.length) {
        filmography.members = filmography.members.filter((candidate) => candidate.tmdbId !== item.tmdbId)
      }
    }
    categoryChoiceKey.value = null
    linkedFeedbackKey.value = key
    window.setTimeout(() => {
      if (linkedFeedbackKey.value === key) linkedFeedbackKey.value = null
    }, 1800)
  } catch {
    if (item.kind === 'movies') movieError.value = 'Não foi possível vincular esta pessoa ao filme.'
    else showError.value = 'Não foi possível vincular esta pessoa à série.'
  } finally {
    linkingKey.value = null
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

.compacted-notice {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
  padding: 14px 16px;
  border-radius: 20px;
  background: var(--base-color-surface-warm);
}

.compacted-notice p {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}

.compacted-notice button {
  padding: 8px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-strong);
  color: var(--base-color-text-primary);
  font: inherit;
  cursor: pointer;
}

.compacted-notice button:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 2px;
}

.mode-switch {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}

.mode-button,
.load-button,
.add-button,
.link-button {
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
.add-button,
.link-button {
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

.member-groups,
.member-group {
  display: grid;
  gap: 18px;
}

.member-group h3 {
  margin: 0;
  font-size: 1rem;
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

.add-button,
.link-button {
  background: var(--base-color-brand-red);
  color: var(--base-color-text-primary);
}

.category-choice {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}

.category-choice button {
  padding: 8px 10px;
  border: 0;
  border-radius: 14px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  cursor: pointer;
}

.linked-feedback {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.78rem;
  font-weight: 700;
}

.mode-button:focus-visible,
.load-button:focus-visible,
.add-button:focus-visible,
.link-button:focus-visible,
.category-choice button:focus-visible,
.tmdb-link:focus-visible,
.poster-link:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 2px;
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
