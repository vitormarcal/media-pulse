<template>
  <main class="list-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página da lista...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar esta lista.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <section class="list-hero">
        <div class="copy">
          <NuxtLink class="back-link" to="/movies/library"> Voltar para biblioteca </NuxtLink>

          <p class="eyebrow">Lista manual</p>
          <h1>{{ data.name }}</h1>
          <p class="intro">{{ heroIntro }}</p>

          <div class="meta-list">
            <span v-for="item in data.heroMeta" :key="item" class="meta-pill">{{ item }}</span>
          </div>
        </div>

        <component :is="spotlightWrapper" :to="spotlightMovie?.href || undefined" class="spotlight-link">
          <article class="spotlight-card">
            <div class="spotlight-poster">
              <img v-if="spotlightImageUrl" :src="spotlightImageUrl" :alt="spotlightMovie?.title || data.name" />
              <div v-else class="spotlight-fallback">{{ (spotlightMovie?.title || data.name).slice(0, 1) }}</div>
            </div>

            <div class="spotlight-body">
              <p class="spotlight-kicker">Primeiro da ordem</p>
              <h2>{{ spotlightMovie?.title || data.name }}</h2>
              <p v-if="spotlightMovie" class="spotlight-subtitle">{{ spotlightMovie.subtitle }}</p>
              <p v-if="spotlightMovie" class="spotlight-meta">{{ spotlightMovie.sessionsLabel }}</p>
              <p v-if="spotlightMovie" class="spotlight-note">{{ spotlightMovie.activityLabel }}</p>
              <p v-else class="spotlight-note">Esta lista ainda não recebeu filmes.</p>
            </div>
          </article>
        </component>
      </section>

      <MoviesLibraryGrid
        eyebrow="Recorte"
        :title="gridTitle"
        :description="gridDescription"
        :summary="gridSummary"
        :items="data.movies"
        layout="aligned"
        empty-message="Nenhum filme entrou nesta lista ainda."
      />
    </template>
  </main>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import MoviesLibraryGrid from '~/components/movies/MoviesLibraryGrid.vue'
import { useMovieListPageData } from '~/composables/useMovieListPageData'

const route = useRoute()
const { resolveMediaUrl } = useMediaUrl()
const slug = computed(() => String(route.params.slug))

const { data, error, status } = await useMovieListPageData(slug.value)

const spotlightMovie = computed(() => data.value?.movies[0] ?? null)
const spotlightImageUrl = computed(() => resolveMediaUrl(spotlightMovie.value?.imageUrl ?? null))
const spotlightWrapper = computed(() => (spotlightMovie.value?.href ? NuxtLink : 'div'))
const heroIntro = computed(() => {
  if (!data.value) return ''
  return data.value.description || 'Um recorte manual da biblioteca.'
})
const gridTitle = computed(() => (data.value ? `Filmes de ${data.value.name}` : 'Os filmes desta lista'))
const gridDescription = computed(() => 'A ordem abaixo preserva a sequência definida manualmente.')
const gridSummary = computed(() =>
  data.value
    ? `${data.value.stats.movieCount} filmes no recorte e ${data.value.stats.watchedMoviesCount} com sessão registrada.`
    : 'Uma nova porta de entrada para a biblioteca.',
)

useHead(() => ({
  title: data.value ? `${data.value.name} · Filmes · Media Pulse` : 'Lista · Filmes · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value
        ? `Filmes ligados à lista ${data.value.name} no Media Pulse.`
        : 'Página interna de lista manual de filmes no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.list-page {
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

.list-hero {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(0, 0.9fr) minmax(20rem, 1.1fr);
  align-items: end;
}

.copy {
  display: grid;
  gap: 12px;
  align-content: end;
}

.back-link {
  width: fit-content;
  padding: 8px 14px;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.eyebrow,
.spotlight-kicker {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.09em;
}

h1 {
  margin: 0;
  font-size: clamp(3rem, 7vw, 5.8rem);
  line-height: 0.92;
  letter-spacing: -0.075em;
}

.intro {
  max-width: 42rem;
  margin: 0;
  color: var(--base-color-text-secondary);
  line-height: 1.62;
}

.meta-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
  margin-top: 2px;
}

.meta-pill {
  padding: 8px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.8rem;
}

.spotlight-link {
  display: block;
}

.spotlight-card {
  display: grid;
  grid-template-columns: minmax(13rem, 0.92fr) minmax(0, 1fr);
  gap: 20px;
  padding: clamp(18px, 3vw, 28px);
  border-radius: 40px;
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(246, 243, 238, 0.98));
  border: 1px solid color-mix(in srgb, var(--base-color-border) 52%, white);
}

.spotlight-poster {
  aspect-ratio: 0.76;
  overflow: hidden;
  border-radius: 28px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.spotlight-poster img,
.spotlight-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.spotlight-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-weight: 700;
  font-size: 3rem;
}

.spotlight-body {
  display: grid;
  align-content: end;
  gap: 8px;
}

h2,
.spotlight-subtitle,
.spotlight-meta,
.spotlight-note {
  margin: 0;
}

h2 {
  font-size: clamp(1.9rem, 3.4vw, 3rem);
  line-height: 0.98;
  letter-spacing: -0.04em;
}

.spotlight-subtitle,
.spotlight-meta {
  color: var(--base-color-text-secondary);
}

.spotlight-note {
  color: var(--base-color-text-muted);
  font-size: 0.88rem;
}

@media (max-width: 980px) {
  .list-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .list-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
