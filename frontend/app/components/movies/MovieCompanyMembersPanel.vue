<template>
  <section class="company-members-panel">
    <SectionHeading
      eyebrow="Catálogo TMDb"
      :title="company.name"
      description="Uma expansão sob demanda da filmografia desta empresa, cruzada com o catálogo local."
      :summary="panelSummary"
    />

    <div class="members-grid">
      <div class="company-logo">
        <img v-if="resolvedLogoUrl" :src="resolvedLogoUrl" :alt="company.name" />
        <div v-else class="logo-fallback">{{ company.name.slice(0, 1) }}</div>
      </div>

      <div class="members-content">
        <div class="members-toolbar">
          <p class="members-count">{{ countLabel }}</p>
          <button
            v-if="showLoadButton"
            type="button"
            class="members-load-button"
            :disabled="loading"
            @click="loadMembers"
          >
            {{ loading ? 'Carregando...' : loadButtonLabel }}
          </button>
        </div>

        <p v-if="errorMessage" class="members-error">{{ errorMessage }}</p>

        <div v-if="members?.members.length" class="movie-rail">
          <article
            v-for="item in members.members"
            :key="item.tmdbId"
            class="member-card"
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
  MovieCompanyMember,
  MovieCompanyMembersResponse,
  MovieCompanyPageData,
} from '~/types/movies'

const NuxtLink = resolveComponent('NuxtLink')

const props = defineProps<{
  company: MovieCompanyPageData
}>()

const emit = defineEmits<{
  added: [response: ManualMovieCatalogCreateResponse]
}>()

const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const resolvedLogoUrl = computed(() => resolveMediaUrl(props.company.logoUrl))
const members = ref<MovieCompanyMembersResponse | null>(null)
const loading = ref(false)
const errorMessage = ref<string | null>(null)
const addingTmdbId = ref<string | null>(null)

const showLoadButton = computed(() => !members.value || errorMessage.value != null)
const loadButtonLabel = computed(() => (errorMessage.value ? 'Tentar novamente' : 'Ver catálogo TMDb'))
const countLabel = computed(() => {
  if (!members.value) return `${props.company.stats.movieCount} no catálogo local`
  const catalogued = members.value.members.filter((member) => member.inCatalog).length
  return members.value.members.length
    ? `${catalogued}/${members.value.members.length} no catálogo`
    : 'Nenhum filme retornado pelo TMDb'
})
const panelSummary = computed(() => {
  if (!members.value) {
    return `${props.company.stats.movieCount} filmes locais ligados a esta empresa.`
  }

  const catalogued = members.value.members.filter((member) => member.inCatalog).length
  return `${catalogued}/${members.value.members.length} filmes do TMDb já estão no catálogo.`
})

async function loadMembers() {
  if (loading.value) return

  loading.value = true
  errorMessage.value = null

  try {
    members.value = await $fetch<MovieCompanyMembersResponse>(
      `/api/movies/companies/${props.company.companyId}/tmdb-members`,
      {
        baseURL: config.public.apiBase,
      },
    )
  } catch {
    errorMessage.value = 'Não foi possível carregar os filmes desta empresa no TMDb.'
  } finally {
    loading.value = false
  }
}

async function addMember(item: MovieCompanyMember) {
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

    const member = members.value?.members.find((candidate) => candidate.tmdbId === item.tmdbId)
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
.company-members-panel {
  display: grid;
  gap: 24px;
}

.members-grid {
  display: grid;
  grid-template-columns: minmax(12rem, 0.36fr) minmax(0, 1fr);
  gap: 22px;
  align-items: start;
}

.company-logo {
  position: sticky;
  top: 92px;
  display: grid;
  place-items: center;
  min-height: 320px;
  padding: 32px;
  overflow: hidden;
  border: 8px solid #ffffff;
  border-radius: 28px;
  background: var(--base-color-surface-soft);
}

.company-logo img {
  max-width: 100%;
  max-height: 7rem;
  object-fit: contain;
}

.logo-fallback,
.movie-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-primary);
  font-size: 4rem;
  font-weight: 700;
}

.members-content {
  display: grid;
  gap: 16px;
}

.members-toolbar {
  display: flex;
  flex-wrap: wrap;
  justify-content: space-between;
  gap: 12px;
  align-items: center;
}

.members-count,
.members-error,
.movie-year {
  margin: 0;
}

.members-count {
  color: var(--base-color-text-secondary);
}

.members-load-button,
.movie-button {
  border: 0;
  border-radius: 16px;
  padding: 8px 14px;
  font: inherit;
  cursor: pointer;
}

.members-load-button {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
}

.members-error {
  color: #7a1414;
}

.movie-rail {
  display: grid;
  grid-auto-flow: column;
  grid-auto-columns: minmax(15rem, 17rem);
  gap: 16px;
  overflow-x: auto;
  padding-bottom: 8px;
}

.member-card {
  display: grid;
  gap: 10px;
  padding: 16px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.8);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 42%, white);
}

.member-card.catalogued {
  background: color-mix(in srgb, var(--base-color-surface-wash) 65%, white);
}

.movie-poster {
  overflow: hidden;
  aspect-ratio: 0.74;
  border-radius: 18px;
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
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.08em;
  text-transform: uppercase;
}

.movie-copy strong {
  font-size: 1rem;
  line-height: 1.2;
}

.movie-year {
  color: var(--base-color-text-secondary);
}

.movie-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
}

.tmdb-link {
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
}

@media (max-width: 980px) {
  .members-grid {
    grid-template-columns: 1fr;
  }

  .company-logo {
    position: static;
    min-height: 200px;
  }
}
</style>
