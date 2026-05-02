<template>
  <main class="collection-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a coleção...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar esta coleção.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="pageData">
      <MoviesCollectionHero
        :title="pageData.hero.title"
        :intro="pageData.hero.intro"
        :lead="pageData.hero.lead"
        :supporting="pageData.hero.supporting"
        :back-link="pageData.hero.backLink"
        :back-label="pageData.hero.backLabel"
        :accent-link="pageData.hero.accentLink"
        :accent-label="pageData.hero.accentLabel"
      />

      <MoviesCollectionContext
        :eyebrow="pageData.context.eyebrow"
        :title="pageData.context.title"
        :description="pageData.context.description"
        :summary="pageData.context.summary"
        :metrics="pageData.context.metrics"
      />

      <section class="members-section">
        <SectionHeading
          eyebrow="Coleção completa"
          title="Todos os filmes deste recorte"
          description="Parte já existe no catálogo local, parte ainda aparece como extensão externa da franquia. A página serve para navegar pelos dois lados sem trocar de contexto."
          :summary="pageData.context.summary"
        />

        <div class="members-masonry">
          <article
            v-for="member in pageData.members"
            :key="member.id"
            class="member-card"
            :class="{ 'member-card--catalogued': member.inCatalog }"
          >
            <component :is="member.href ? NuxtLink : 'div'" class="member-poster-link" :to="member.href || undefined">
              <div class="member-poster">
                <img
                  v-if="resolveMediaUrl(member.imageUrl)"
                  :src="resolveMediaUrl(member.imageUrl)"
                  :alt="member.title"
                />
                <div v-else class="member-fallback">{{ member.title.slice(0, 1) }}</div>
              </div>
            </component>

            <div class="member-copy">
              <p class="member-kicker">{{ member.statusLabel }}</p>
              <h2>{{ member.title }}</h2>
              <p class="member-subtitle">{{ member.subtitle }}</p>
              <p class="member-description">{{ member.overview || fallbackOverview(member.inCatalog) }}</p>
            </div>

            <div class="member-footer">
              <span class="meta-pill">{{ member.meta }}</span>
              <div class="member-actions">
                <a class="member-link" :href="member.tmdbUrl" target="_blank" rel="noreferrer">TMDb</a>
                <button
                  v-if="!member.inCatalog"
                  type="button"
                  class="member-button"
                  :disabled="addingTmdbId === member.tmdbId"
                  @click="addMember(member)"
                >
                  {{ addingTmdbId === member.tmdbId ? 'Adicionando...' : 'Adicionar' }}
                </button>
              </div>
            </div>
          </article>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import SectionHeading from '~/components/home/SectionHeading.vue'
import MoviesCollectionContext from '~/components/movies/MoviesCollectionContext.vue'
import MoviesCollectionHero from '~/components/movies/MoviesCollectionHero.vue'
import { useMovieCollectionPageData } from '~/composables/useMovieCollectionPageData'
import { buildMovieCollectionPageData } from '~/utils/movies'
import type {
  ManualMovieCatalogCreateResponse,
  MovieCollectionMembersResponse,
  MovieCollectionPageData,
} from '~/types/movies'

const route = useRoute()
const config = useRuntimeConfig()
const { resolveMediaUrl } = useMediaUrl()
const collectionId = computed(() => String(route.params.id))
const addingTmdbId = ref<string | null>(null)

const { data, error, status, refresh } = await useMovieCollectionPageData(collectionId.value)
const membersResponse = ref<MovieCollectionMembersResponse | null>(null)

watch(
  data,
  (value) => {
    if (!value) {
      membersResponse.value = null
      return
    }

    membersResponse.value = {
      collectionId: value.collectionId,
      tmdbId: value.tmdbId,
      name: value.name,
      overview: value.overview,
      posterUrl: value.posterUrl,
      backdropUrl: value.backdropUrl,
      members: value.members.map((member) => ({
        tmdbId: member.tmdbId,
        title: member.title,
        originalTitle: null,
        year: Number(member.subtitle) || null,
        overview: member.overview,
        posterUrl: member.imageUrl,
        backdropUrl: null,
        tmdbUrl: member.tmdbUrl,
        localMovieId: member.href ? Number(member.href.split('/').pop()) || null : null,
        localSlug: member.href ? member.href.split('/').pop() || null : null,
        inCatalog: member.inCatalog,
      })),
    }
  },
  { immediate: true },
)

const pageData = computed<MovieCollectionPageData | null>(() =>
  membersResponse.value ? buildMovieCollectionPageData(membersResponse.value) : data.value,
)

function fallbackOverview(inCatalog: boolean) {
  return inCatalog
    ? 'Este filme já está no catálogo local e participa do recorte completo da coleção.'
    : 'Este filme ainda não entrou no catálogo local, mas já aparece como parte da coleção no TMDb.'
}

async function addMember(member: MovieCollectionPageData['members'][number]) {
  if (addingTmdbId.value) return

  addingTmdbId.value = member.tmdbId
  try {
    const response = await $fetch<ManualMovieCatalogCreateResponse>('/api/movies/catalog', {
      baseURL: config.public.apiBase,
      method: 'POST',
      body: {
        title: member.title,
        year: Number(member.subtitle) || null,
        tmdbId: member.tmdbId,
        imdbId: null,
      },
    })

    const target = membersResponse.value?.members.find((candidate) => candidate.tmdbId === member.tmdbId)
    if (target) {
      target.inCatalog = true
      target.localMovieId = response.movieId
      target.localSlug = response.slug
    } else {
      await refresh()
    }
  } finally {
    addingTmdbId.value = null
  }
}

useHead(() => ({
  title: pageData.value ? `${pageData.value.name} · Coleção · Filmes · Media Pulse` : 'Coleção · Filmes · Media Pulse',
  meta: [
    {
      name: 'description',
      content: pageData.value
        ? `Página da coleção ${pageData.value.name} na biblioteca de filmes do Media Pulse.`
        : 'Página de coleção de filmes no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.collection-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.state-card {
  padding: 24px;
  border-radius: 24px;
  background: rgba(255, 255, 255, 0.82);
  color: var(--base-color-text-secondary);
}

.state-card.error {
  color: #7a1414;
}

pre {
  margin: 12px 0 0;
  white-space: pre-wrap;
}

.members-section {
  display: grid;
  gap: 24px;
}

.members-masonry {
  column-count: 4;
  column-gap: 18px;
}

.member-card {
  break-inside: avoid;
  display: grid;
  gap: 14px;
  margin-bottom: 18px;
  padding: 12px 12px 18px;
  border-radius: 28px;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.06), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(246, 243, 238, 0.98));
}

.member-card--catalogued {
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.98), rgba(247, 243, 237, 0.99));
}

.member-poster-link {
  color: inherit;
  text-decoration: none;
}

.member-poster {
  overflow: hidden;
  aspect-ratio: 0.74;
  border-radius: 22px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.member-poster img,
.member-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.member-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-size: 2.4rem;
  font-weight: 700;
}

.member-copy {
  display: grid;
  gap: 6px;
}

.member-kicker {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.72rem;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

h2,
.member-subtitle,
.member-description {
  margin: 0;
}

h2 {
  font-size: 1.28rem;
  line-height: 1.04;
  letter-spacing: -0.04em;
}

.member-subtitle,
.member-description {
  color: var(--base-color-text-secondary);
}

.member-description {
  line-height: 1.58;
  font-size: 0.92rem;
}

.member-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  align-items: center;
  justify-content: space-between;
}

.meta-pill {
  padding: 8px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.76rem;
}

.member-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
}

.member-link,
.member-button {
  border: 0;
  padding: 8px 14px;
  border-radius: 16px;
  font: inherit;
  font-size: 0.76rem;
}

.member-link {
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  text-decoration: none;
}

.member-button {
  background: var(--base-color-brand-red);
  color: #000;
  cursor: pointer;
}

.member-button:disabled {
  cursor: wait;
  opacity: 0.64;
}

@media (max-width: 1280px) {
  .members-masonry {
    column-count: 3;
  }
}

@media (max-width: 900px) {
  .members-masonry {
    column-count: 2;
  }
}

@media (max-width: 640px) {
  .collection-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }

  .members-masonry {
    column-count: 1;
  }
}
</style>
