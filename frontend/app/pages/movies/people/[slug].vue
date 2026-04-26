<template>
  <main class="person-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página da pessoa...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar esta pessoa.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <section class="person-hero">
        <div class="copy">
          <NuxtLink class="back-link" to="/movies/library"> Voltar para biblioteca </NuxtLink>

          <p class="eyebrow">Pessoa</p>
          <h1>{{ data.name }}</h1>
          <p class="intro">{{ heroIntro }}</p>

          <div class="meta-list">
            <span v-for="item in data.heroMeta" :key="item" class="meta-pill">{{ item }}</span>
          </div>
        </div>

        <div class="portrait-card">
          <div class="portrait-frame">
            <img v-if="resolveMediaUrl(data.profileUrl)" :src="resolveMediaUrl(data.profileUrl)" :alt="data.name" />
            <div v-else class="portrait-fallback">{{ data.name.slice(0, 1) }}</div>
          </div>
        </div>
      </section>

      <MoviesLibraryGrid
        eyebrow="Catálogo local"
        :title="gridTitle"
        :description="gridDescription"
        :summary="gridSummary"
        :items="data.movies"
        layout="aligned"
        empty-message="Nenhum filme local apareceu ligado a esta pessoa."
      />

      <MoviePersonFilmographyPanel :person="data" @added="handleCatalogAdded" />
    </template>
  </main>
</template>

<script setup lang="ts">
import MoviePersonFilmographyPanel from '~/components/movies/MoviePersonFilmographyPanel.vue'
import MoviesLibraryGrid from '~/components/movies/MoviesLibraryGrid.vue'
import { useMoviePersonPageData } from '~/composables/useMoviePersonPageData'

const route = useRoute()
const { resolveMediaUrl } = useMediaUrl()
const slug = computed(() => String(route.params.slug))

const { data, error, status, refresh } = await useMoviePersonPageData(slug.value)

const heroIntro = computed(() => {
  if (!data.value) return ''
  return data.value.roles.length
    ? `Um recorte da filmoteca por ${data.value.roles.join(', ').toLowerCase()}, com os filmes locais já ligados a esta pessoa e a expansão da filmografia do TMDb sob demanda.`
    : 'Um recorte da filmoteca atravessado por esta pessoa.'
})

const gridTitle = computed(() => (data.value ? `Os filmes locais ligados a ${data.value.name}` : 'Filmes desta pessoa'))
const gridDescription = computed(() => 'O catálogo local primeiro, antes da expansão da filmografia completa.')
const gridSummary = computed(() =>
  data.value
    ? `${data.value.stats.movieCount} filmes locais e ${data.value.stats.watchedMoviesCount} com sessão registrada.`
    : 'Uma nova porta de entrada para a biblioteca.',
)

async function handleCatalogAdded() {
  await refresh()
}

useHead(() => ({
  title: data.value ? `${data.value.name} · Filmes · Media Pulse` : 'Pessoa · Filmes · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value
        ? `Filmes ligados a ${data.value.name} no Media Pulse.`
        : 'Página interna de pessoa ligada à biblioteca de filmes no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.person-page {
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

.person-hero {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(0, 0.9fr) minmax(20rem, 0.7fr);
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

.eyebrow {
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

.portrait-card {
  display: grid;
}

.portrait-frame {
  overflow: hidden;
  min-height: 24rem;
  border: 8px solid #ffffff;
  border-radius: 40px;
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(246, 243, 238, 0.98));
}

.portrait-frame img,
.portrait-fallback {
  width: 100%;
  height: 100%;
  min-height: 24rem;
  object-fit: cover;
}

.portrait-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-size: 4rem;
  font-weight: 700;
}

@media (max-width: 980px) {
  .person-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .person-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
