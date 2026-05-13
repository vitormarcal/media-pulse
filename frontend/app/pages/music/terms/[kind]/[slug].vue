<template>
  <main class="term-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página do termo...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar este termo.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <section class="term-hero" :style="heroShellStyle">
        <div class="copy">
          <NuxtLink class="back-link" to="/music?view=archive&kind=albums"> Voltar para álbuns </NuxtLink>

          <p class="eyebrow">{{ data.kind === 'GENRE' ? 'Gênero' : 'Tag' }}</p>
          <h1>{{ data.name }}</h1>
          <p class="intro">{{ heroIntro }}</p>

          <div class="meta-list">
            <span v-for="item in data.heroMeta" :key="item" class="meta-pill">{{ item }}</span>
          </div>
        </div>

        <component :is="spotlightWrapper" :to="spotlightAlbum?.href || undefined" class="spotlight-link">
          <article class="spotlight-card">
            <div class="spotlight-cover">
              <img v-if="spotlightImageUrl" :src="spotlightImageUrl" :alt="spotlightAlbum?.title || data.name" />
              <div v-else class="spotlight-fallback">{{ (spotlightAlbum?.title || data.name).slice(0, 1) }}</div>
            </div>

            <div class="spotlight-body">
              <p class="spotlight-kicker">Entrada do recorte</p>
              <h2>{{ spotlightAlbum?.title || data.name }}</h2>
              <p v-if="spotlightAlbum" class="spotlight-subtitle">{{ spotlightAlbum.subtitle }}</p>
              <p v-if="spotlightAlbum" class="spotlight-meta">{{ spotlightAlbum.meta }}</p>
              <p v-if="spotlightAlbum" class="spotlight-note">{{ spotlightAlbum.aside }}</p>
              <p v-else class="spotlight-note">Ainda não há um álbum ativo para abrir esse recorte.</p>
            </div>
          </article>
        </component>
      </section>

      <MusicLibraryGrid
        eyebrow="Recorte"
        :title="gridTitle"
        :description="gridDescription"
        :summary="gridSummary"
        :items="data.albums"
        empty-message="Nenhum álbum ativo apareceu para esse termo."
      />
    </template>
  </main>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import MusicLibraryGrid from '~/components/music/MusicLibraryGrid.vue'
import { useAlbumTermPageData } from '~/composables/useAlbumTermPageData'

const route = useRoute()
const { resolveMediaUrl } = useMediaUrl()
const kind = computed(() => String(route.params.kind))
const slug = computed(() => String(route.params.slug))

const { data, error, status } = await useAlbumTermPageData(kind.value, slug.value)

const spotlightAlbum = computed(() => data.value?.albums[0] ?? null)
const spotlightImageUrl = computed(() => resolveMediaUrl(spotlightAlbum.value?.imageUrl ?? null))
const spotlightWrapper = computed(() => (spotlightAlbum.value?.href ? NuxtLink : 'div'))
const heroShellStyle = computed(() =>
  spotlightImageUrl.value
    ? {
        backgroundImage: `linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(246, 243, 238, 0.97)), radial-gradient(circle at top right, rgba(230, 0, 35, 0.1), transparent 28%), url("${spotlightImageUrl.value}")`,
      }
    : undefined,
)
const heroIntro = computed(() =>
  data.value?.kind === 'GENRE'
    ? 'Um corte mais amplo do arquivo de música, útil para percorrer discos por afinidade de linguagem.'
    : 'Um corte mais específico do arquivo, reunindo álbuns por clima, cena, fase ou qualquer marcação manual que faça sentido.',
)
const gridTitle = computed(() => (data.value ? `Álbuns com ${data.value.name}` : 'Os álbuns deste termo'))
const gridDescription = computed(() =>
  data.value?.kind === 'GENRE'
    ? 'A mesma estante, agora filtrada por um gênero específico.'
    : 'A mesma estante, agora filtrada por uma marcação mais livre.',
)
const gridSummary = computed(() =>
  data.value
    ? `${data.value.stats.albumCount} álbuns no recorte e ${data.value.stats.playedAlbumsCount} com plays registrados.`
    : 'Uma nova porta de entrada para os discos do arquivo.',
)

useHead(() => ({
  title: data.value ? `${data.value.name} · Álbuns · Media Pulse` : 'Termo · Álbuns · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value
        ? `Álbuns ligados ao termo ${data.value.name} no Media Pulse.`
        : 'Página interna de termo de álbum.',
    },
  ],
}))
</script>

<style scoped>
.term-page {
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

.term-hero {
  display: grid;
  gap: 24px;
  grid-template-columns: minmax(0, 0.9fr) minmax(20rem, 1.1fr);
  align-items: end;
  padding: clamp(24px, 4vw, 36px);
  border-radius: 40px;
  background-image:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.98));
  background-size: cover;
  background-position: center;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
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

.spotlight-cover {
  aspect-ratio: 1;
  overflow: hidden;
  border-radius: 28px;
  border: 8px solid #fff;
  background: var(--base-color-surface-soft);
}

.spotlight-cover img,
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
}

.spotlight-subtitle,
.spotlight-note {
  color: var(--base-color-text-secondary);
}

.spotlight-meta {
  font-size: 0.84rem;
}

@media (max-width: 980px) {
  .term-hero,
  .spotlight-card {
    grid-template-columns: 1fr;
  }
}

@media (max-width: 720px) {
  .term-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
