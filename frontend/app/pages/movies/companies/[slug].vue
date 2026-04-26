<template>
  <main class="company-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página da empresa...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar esta empresa.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <section class="company-hero">
        <div class="copy">
          <NuxtLink class="back-link" to="/movies/library"> Voltar para biblioteca </NuxtLink>

          <p class="eyebrow">Empresa</p>
          <h1>{{ data.name }}</h1>
          <p class="intro">{{ heroIntro }}</p>

          <div class="meta-list">
            <span v-for="item in data.heroMeta" :key="item" class="meta-pill">{{ item }}</span>
          </div>
        </div>

        <div class="logo-card">
          <div class="logo-frame">
            <img v-if="resolveMediaUrl(data.logoUrl)" :src="resolveMediaUrl(data.logoUrl)" :alt="data.name" />
            <div v-else class="logo-fallback">{{ data.name.slice(0, 1) }}</div>
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
        empty-message="Nenhum filme local apareceu ligado a esta empresa."
      />

      <MovieCompanyMembersPanel :company="data" @added="handleCatalogAdded" />
    </template>
  </main>
</template>

<script setup lang="ts">
import MovieCompanyMembersPanel from '~/components/movies/MovieCompanyMembersPanel.vue'
import MoviesLibraryGrid from '~/components/movies/MoviesLibraryGrid.vue'
import { useMovieCompanyPageData } from '~/composables/useMovieCompanyPageData'

const route = useRoute()
const { resolveMediaUrl } = useMediaUrl()
const slug = computed(() => String(route.params.slug))

const { data, error, status, refresh } = await useMovieCompanyPageData(slug.value)

const heroIntro = computed(() => {
  if (!data.value) return ''
  return `Um recorte da filmoteca atravessado por ${data.value.typeLabel.toLowerCase()}, com os filmes locais já ligados a ${data.value.name}.`
})

const gridTitle = computed(() =>
  data.value ? `Os filmes locais ligados a ${data.value.name}` : 'Filmes desta empresa',
)
const gridDescription = computed(() => 'O catálogo local primeiro, usando a produtora como novo eixo de navegação.')
const gridSummary = computed(() =>
  data.value
    ? `${data.value.stats.movieCount} filmes locais e ${data.value.stats.watchedMoviesCount} com sessão registrada.`
    : 'Uma nova porta de entrada para a biblioteca.',
)

async function handleCatalogAdded() {
  await refresh()
}

useHead(() => ({
  title: data.value ? `${data.value.name} · Filmes · Media Pulse` : 'Empresa · Filmes · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value
        ? `Filmes ligados a ${data.value.name} no Media Pulse.`
        : 'Página interna de empresa ligada à biblioteca de filmes no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.company-page {
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

.company-hero {
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

.logo-card {
  display: grid;
}

.logo-frame {
  display: grid;
  place-items: center;
  min-height: 24rem;
  padding: 40px;
  overflow: hidden;
  border: 8px solid #ffffff;
  border-radius: 40px;
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(246, 243, 238, 0.98));
}

.logo-frame img {
  max-width: 100%;
  max-height: 10rem;
  object-fit: contain;
}

.logo-fallback {
  display: grid;
  place-items: center;
  width: 100%;
  height: 100%;
  color: var(--base-color-text-secondary);
  font-size: 4rem;
  font-weight: 700;
}

@media (max-width: 980px) {
  .company-hero {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .company-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
