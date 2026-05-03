<template>
  <section class="filmography-panel">
    <SectionHeading
      eyebrow="Filmografia TMDb"
      :title="person.name"
      description="Uma expansão sob demanda da filmografia desta pessoa, cruzada com o catálogo local."
      :summary="panelSummary"
    />

    <div class="filmography-grid">
      <div class="person-portrait">
        <img v-if="resolvedProfileUrl" :src="resolvedProfileUrl" :alt="person.name" />
        <div v-else class="portrait-fallback">{{ person.name.slice(0, 1) }}</div>
      </div>

      <div class="filmography-content">
        <div class="filmography-toolbar">
          <p class="filmography-count">{{ countLabel }}</p>
          <button
            v-if="showLoadButton"
            type="button"
            class="filmography-load-button"
            :disabled="loading"
            @click="loadFilmography"
          >
            {{ loading ? 'Carregando...' : loadButtonLabel }}
          </button>
        </div>

        <p v-if="errorMessage" class="filmography-error">{{ errorMessage }}</p>

        <div v-if="filmography?.members.length" class="movie-rail">
          <article
            v-for="item in filmography.members"
            :key="item.tmdbId"
            class="filmography-card"
            :class="{ catalogued: item.inCatalog }"
          >
            <component
              :is="item.localSlug ? NuxtLink : 'div'"
              class="poster-link"
              :to="item.localSlug ? `/movies/${item.localSlug}` : undefined"
            >
              <div class="movie-poster">
                <img v-if="resolveMediaUrl(item.posterUrl)" :src="resolveMediaUrl(item.posterUrl)" :alt="item.title" />
                <div v-else class="movie-fallback">{{ item.title.slice(0, 1) }}</div>
              </div>
            </component>

            <div class="movie-copy">
              <span>{{ item.inCatalog ? 'Catálogo' : 'Sugestão TMDb' }}</span>
              <strong>{{ item.title }}</strong>
              <p class="movie-year">{{ item.year ? String(item.year) : 'Sem ano' }}</p>
              <p class="movie-role">{{ item.roleLabel }}</p>
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
import type {
  ManualMovieCatalogCreateResponse,
  PersonFilmographyMember,
  PersonFilmographyResponse,
  PersonPageData,
} from '~/types/movies'

const NuxtLink = resolveComponent('NuxtLink')

const props = defineProps<{
  person: PersonPageData
}>()

const emit = defineEmits<{
  added: [response: ManualMovieCatalogCreateResponse]
}>()

const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const resolvedProfileUrl = computed(() => resolveMediaUrl(props.person.profileUrl))
const filmography = ref<PersonFilmographyResponse | null>(null)
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const addingTmdbId = ref<string | null>(null)

const showLoadButton = computed(() => !filmography.value || errorMessage.value != null)
const loadButtonLabel = computed(() => (errorMessage.value ? 'Tentar novamente' : 'Ver filmografia'))
const countLabel = computed(() => {
  if (!filmography.value) return `${props.person.stats.movieCount} no catálogo local`
  const catalogued = filmography.value.members.filter((member) => member.inCatalog).length
  return filmography.value.members.length
    ? `${catalogued}/${filmography.value.members.length} no catálogo`
    : 'Nenhum filme retornado pelo TMDb'
})
const panelSummary = computed(() => {
  if (!filmography.value) {
    return `${props.person.stats.movieCount} filmes locais ligados a esta pessoa.`
  }

  const catalogued = filmography.value.members.filter((member) => member.inCatalog).length
  return `${catalogued}/${filmography.value.members.length} filmes da filmografia já estão no catálogo.`
})

async function loadFilmography() {
  if (loading.value) return

  loading.value = true
  errorMessage.value = null

  try {
    filmography.value = await $fetch<PersonFilmographyResponse>(
      `/api/people/${props.person.personId}/tmdb-filmography`,
      {
        baseURL: config.public.apiBase,
      },
    )
  } catch {
    errorMessage.value = 'Não foi possível carregar a filmografia desta pessoa.'
  } finally {
    loading.value = false
  }
}

async function addMember(item: PersonFilmographyMember) {
  if (addingTmdbId.value || !item.tmdbId) return

  addingTmdbId.value = item.tmdbId
  errorMessage.value = null

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

    const member = filmography.value?.members.find((candidate) => candidate.tmdbId === item.tmdbId)
    if (member) {
      member.inCatalog = true
      member.localMovieId = response.movieId
      member.localSlug = response.slug
    }
    emit('added', response)
  } catch {
    errorMessage.value = `Não foi possível adicionar "${item.title}".`
  } finally {
    addingTmdbId.value = null
  }
}
</script>

<style scoped>
.filmography-panel {
  display: grid;
  gap: 24px;
}

.filmography-grid {
  display: grid;
  grid-template-columns: minmax(12rem, 0.36fr) minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.person-portrait {
  position: sticky;
  top: 92px;
  overflow: hidden;
  min-height: 320px;
  border: 8px solid #ffffff;
  border-radius: 28px;
  background: var(--base-color-surface-soft);
}

.person-portrait img,
.portrait-fallback {
  width: 100%;
  height: 100%;
  min-height: 320px;
  object-fit: cover;
}

.portrait-fallback,
.movie-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-primary);
  font-size: 4rem;
  font-weight: 700;
}

.filmography-content {
  display: grid;
  gap: 16px;
}

.filmography-toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.filmography-count,
.filmography-error,
.movie-year,
.movie-role {
  margin: 0;
}

.filmography-count {
  color: var(--base-color-text-secondary);
}

.filmography-load-button,
.movie-button {
  border: 0;
  border-radius: 16px;
  padding: 8px 14px;
  font: inherit;
  cursor: pointer;
}

.filmography-load-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.filmography-error {
  color: #7a1414;
}

.movie-rail {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(15rem, 17rem);
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 6px;
}

.filmography-card {
  display: grid;
  gap: 12px;
  padding: 16px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.88);
}

.poster-link {
  display: block;
}

.movie-poster {
  aspect-ratio: 0.72;
  overflow: hidden;
  border-radius: 20px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.movie-poster img,
.movie-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.movie-copy {
  display: grid;
  gap: 4px;
}

.movie-copy span {
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.06em;
  text-transform: uppercase;
}

.movie-copy strong {
  font-size: 1.08rem;
  line-height: 1.05;
}

.movie-year,
.movie-role {
  color: var(--base-color-text-secondary);
  font-size: 0.82rem;
}

.movie-actions {
  display: flex;
  gap: 10px;
  align-items: center;
}

.movie-button {
  background: var(--base-color-brand-red);
  color: #000000;
}

.tmdb-link {
  color: var(--base-color-text-primary);
  font-size: 0.82rem;
}

.filmography-card.catalogued {
  background: color-mix(in srgb, rgba(255, 255, 255, 0.92) 84%, rgba(224, 224, 217, 0.82));
}

.filmography-load-button:disabled,
.movie-button:disabled {
  opacity: 0.6;
  cursor: default;
}

@media (max-width: 980px) {
  .filmography-grid {
    grid-template-columns: 1fr;
  }

  .person-portrait {
    position: static;
    min-height: 280px;
  }
}
</style>
