<template>
  <section v-if="collection" class="collection-panel">
    <SectionHeading
      eyebrow="Coleção"
      :title="collection.name"
      description="A franquia ao redor deste filme, entre catálogo local e sugestões do TMDb."
      :summary="collectionSummary"
    />

    <div class="collection-grid">
      <div class="collection-poster">
        <img v-if="resolvedPosterUrl" :src="resolvedPosterUrl" :alt="collection.name" />
        <div v-else class="poster-fallback">{{ collection.name.slice(0, 1) }}</div>
      </div>

      <div class="collection-content">
        <div class="collection-toolbar">
          <p class="collection-count">{{ collectionCountLabel }}</p>
          <button
            v-if="showLoadButton"
            type="button"
            class="collection-load-button"
            :disabled="loadingMembers"
            @click="loadMembers"
          >
            {{ membersButtonLabel }}
          </button>
        </div>

        <p v-if="membersError" class="members-error">{{ membersError }}</p>

        <div class="movie-rail">
          <article
            v-for="item in collectionItems"
            :key="item.id"
            class="collection-card"
            :class="{ current: item.current, catalogued: item.inCatalog }"
          >
            <component :is="item.href ? NuxtLink : 'div'" class="poster-link" :to="item.href || undefined">
              <div class="movie-poster">
                <img v-if="resolveMediaUrl(item.imageUrl)" :src="resolveMediaUrl(item.imageUrl)" :alt="item.title" />
                <div v-else class="movie-fallback">{{ item.title.slice(0, 1) }}</div>
              </div>
            </component>

            <div class="movie-copy">
              <span>{{ item.statusLabel }}</span>
              <strong>{{ item.title }}</strong>
              <p class="movie-year">{{ item.subtitle }}</p>
            </div>

            <div v-if="item.tmdbUrl || !item.inCatalog" class="movie-actions">
              <a v-if="item.tmdbUrl" class="tmdb-link" :href="item.tmdbUrl" target="_blank" rel="noreferrer">TMDb</a>
              <button
                v-if="!item.inCatalog"
                type="button"
                class="movie-button"
                :disabled="addingTmdbId === item.tmdbId"
                @click="addMember(item)"
              >
                {{ addingTmdbId === item.tmdbId ? 'Adicionando...' : 'Adicionar' }}
              </button>
            </div>
          </article>
        </div>
      </div>
    </div>
  </section>
</template>

<script setup lang="ts">
import SectionHeading from '~/components/home/SectionHeading.vue'
import type { ManualMovieCatalogCreateResponse, MovieCollectionMembersResponse, MoviePageData } from '~/types/movies'

const NuxtLink = resolveComponent('NuxtLink')

const props = defineProps<{
  collection: MoviePageData['collection']
}>()

const emit = defineEmits<{
  added: [response: ManualMovieCatalogCreateResponse]
}>()

const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const resolvedPosterUrl = computed(() => resolveMediaUrl(props.collection?.posterUrl ?? props.collection?.backdropUrl))
const membersResponse = ref<MovieCollectionMembersResponse | null>(null)
const loadingMembers = ref(false)
const membersError = ref<string | null>(null)
const addingTmdbId = ref<string | null>(null)

interface CollectionDisplayItem {
  id: string
  tmdbId: string | null
  title: string
  year: number | null
  subtitle: string
  imageUrl: string | null
  href: string | null
  tmdbUrl: string | null
  inCatalog: boolean
  current: boolean
  statusLabel: string
}

const collectionSummary = computed(() => {
  if (!props.collection) return ''
  if (!membersResponse.value) return props.collection.progressLabel

  return membersCatalogSummary.value
})

const membersButtonLabel = computed(() => {
  if (loadingMembers.value) return 'Carregando...'
  return membersError.value ? 'Tentar novamente' : 'Ver todos'
})

const showLoadButton = computed(() => !membersResponse.value || membersError.value != null)

const membersCatalogSummary = computed(() => {
  const members = membersResponse.value?.members ?? []
  const catalogued = members.filter((member) => member.inCatalog).length
  return members.length ? `${catalogued}/${members.length} no catálogo` : 'Nenhum membro retornado pelo TMDb'
})

const collectionItems = computed<CollectionDisplayItem[]>(() => {
  if (!props.collection) return []

  if (!membersResponse.value) {
    return props.collection.movies.map((movie) => ({
      id: `local-${movie.id}`,
      tmdbId: null,
      title: movie.title,
      year: null,
      subtitle: movie.subtitle,
      imageUrl: movie.imageUrl,
      href: movie.href,
      tmdbUrl: null,
      inCatalog: true,
      current: movie.current,
      statusLabel: movie.current ? 'Este filme' : movie.watched ? 'Assistido' : 'Catálogo',
    }))
  }

  return membersResponse.value.members.map((member) => ({
    id: `tmdb-${member.tmdbId}`,
    tmdbId: member.tmdbId,
    title: member.title,
    year: member.year,
    subtitle: member.year ? String(member.year) : 'Sem ano',
    imageUrl: member.posterUrl,
    href: member.localSlug ? `/movies/${member.localSlug}` : null,
    tmdbUrl: member.tmdbUrl,
    inCatalog: member.inCatalog,
    current: member.localMovieId === props.collection?.movies.find((movie) => movie.current)?.id,
    statusLabel:
      member.localMovieId === props.collection?.movies.find((movie) => movie.current)?.id
        ? 'Este filme'
        : member.inCatalog
          ? 'Catálogo'
          : 'Sugestão TMDb',
  }))
})

const collectionCountLabel = computed(() => {
  if (!membersResponse.value) return `${props.collection?.movies.length ?? 0} no catálogo local`
  return membersCatalogSummary.value
})

async function loadMembers() {
  if (!props.collection || loadingMembers.value) return

  loadingMembers.value = true
  membersError.value = null

  try {
    membersResponse.value = await $fetch<MovieCollectionMembersResponse>(
      `/api/movies/collections/${props.collection.id}/tmdb-members`,
      {
        baseURL: config.public.apiBase,
      },
    )
  } catch {
    membersError.value = 'Não foi possível carregar a coleção completa.'
  } finally {
    loadingMembers.value = false
  }
}

async function addMember(item: CollectionDisplayItem) {
  if (addingTmdbId.value || !item.tmdbId) return

  addingTmdbId.value = item.tmdbId
  membersError.value = null

  try {
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

    const member = membersResponse.value?.members.find((candidate) => candidate.tmdbId === item.tmdbId)
    if (member) {
      member.inCatalog = true
      member.localMovieId = response.movieId
      member.localSlug = response.slug
    }
    emit('added', response)
  } catch {
    membersError.value = `Não foi possível adicionar "${item.title}".`
  } finally {
    addingTmdbId.value = null
  }
}
</script>

<style scoped>
.collection-panel {
  display: grid;
  gap: 24px;
}

.collection-grid {
  display: grid;
  grid-template-columns: minmax(12rem, 0.36fr) minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.collection-poster {
  position: sticky;
  top: 92px;
  overflow: hidden;
  min-height: 320px;
  border: 8px solid #ffffff;
  border-radius: 28px;
  background: var(--base-color-surface-soft);
}

.collection-poster img,
.poster-fallback {
  width: 100%;
  height: 100%;
  min-height: 320px;
  object-fit: cover;
}

.poster-fallback,
.movie-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-primary);
  font-size: 4rem;
  font-weight: 700;
  background: var(--base-color-surface-strong);
}

.collection-content {
  display: grid;
  gap: 14px;
  align-content: start;
}

.collection-toolbar {
  display: flex;
  flex-wrap: wrap;
  gap: 12px;
  align-items: center;
  justify-content: space-between;
}

.collection-count {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
  font-weight: 700;
}

.collection-load-button {
  border: 2px solid transparent;
  border-radius: 16px;
  background: var(--base-color-surface-strong);
  color: var(--base-color-text-primary);
  cursor: pointer;
  font: inherit;
  font-size: 0.75rem;
  font-weight: 700;
  padding: 6px 14px;
}

.collection-load-button:disabled {
  cursor: wait;
  opacity: 0.62;
}

.movie-rail {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(152px, 1fr));
  gap: 18px 14px;
}

.collection-card {
  display: grid;
  grid-template-rows: auto 1fr auto;
  gap: 10px;
  min-width: 0;
  color: inherit;
  text-decoration: none;
}

.poster-link {
  color: inherit;
  text-decoration: none;
}

.movie-poster {
  overflow: hidden;
  aspect-ratio: 2 / 3;
  border: 8px solid #ffffff;
  border-radius: 24px;
  background: linear-gradient(160deg, rgba(230, 0, 35, 0.08), rgba(33, 25, 34, 0.05)), var(--base-color-surface-soft);
}

.collection-card:not(.catalogued) .movie-poster {
  border-color: color-mix(in srgb, var(--base-color-surface-warm) 74%, #ffffff);
}

.movie-poster img,
.movie-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.movie-fallback {
  font-size: 2.5rem;
}

.movie-copy {
  display: grid;
  gap: 5px;
}

.movie-copy span {
  color: var(--base-color-text-secondary);
  font-size: 0.75rem;
  font-weight: 700;
  text-transform: uppercase;
}

.collection-card.current .movie-copy span {
  color: var(--base-color-brand-red);
}

.collection-card:not(.catalogued) .movie-copy span {
  color: var(--base-color-text-secondary);
}

.movie-copy strong {
  color: var(--base-color-text-primary);
  line-height: 1.1;
}

.movie-copy p {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}

.members-error {
  margin: 0;
  color: #7a1414;
  font-size: 0.9rem;
}

.movie-year {
  margin: 0;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}

.movie-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  justify-content: space-between;
}

.tmdb-link {
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
  font-weight: 700;
  text-decoration: none;
}

.tmdb-link:hover {
  color: var(--base-color-text-primary);
}

.movie-button {
  border: 2px solid transparent;
  border-radius: 16px;
  background: var(--base-color-surface-strong);
  color: var(--base-color-text-primary);
  cursor: pointer;
  font: inherit;
  font-size: 0.75rem;
  font-weight: 700;
  justify-content: center;
  min-width: 0;
  padding: 6px 14px;
  text-align: center;
  text-decoration: none;
}

.movie-button:hover:not(:disabled) {
  background: var(--base-color-surface-warm);
}

.movie-button:disabled {
  cursor: wait;
  opacity: 0.7;
}

@media (max-width: 900px) {
  .collection-grid {
    grid-template-columns: 1fr;
  }

  .collection-poster {
    position: static;
    min-height: 220px;
  }

  .collection-poster img,
  .poster-fallback {
    min-height: 220px;
  }
}
</style>
