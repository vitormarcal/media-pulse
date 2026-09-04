<template>
  <main class="term-page">
    <div v-if="status === 'pending'" class="state-card"><p>Carregando...</p></div>
    <div v-else-if="error" class="state-card error"><p>Não foi possível carregar este termo.</p></div>

    <template v-else-if="data">
      <section class="term-hero" :style="heroShellStyle">
        <div class="copy">
          <NuxtLink class="back-link" to="/shows">Voltar para séries</NuxtLink>
          <p class="eyebrow">{{ data.kind === 'GENRE' ? 'Gênero' : 'Tag' }}</p>
          <h1>{{ data.name }}</h1>
          <p class="intro">{{ heroIntro }}</p>
          <div class="meta-list">
            <span v-for="item in data.heroMeta" :key="item" class="meta-pill">{{ item }}</span>
          </div>
        </div>

        <component :is="spotlightWrapper" :to="spotlightShow?.href || undefined" class="spotlight-link">
          <article class="spotlight-card">
            <div class="spotlight-poster">
              <img v-if="spotlightImageUrl" :src="spotlightImageUrl" :alt="spotlightShow?.title || data.name" />
              <div v-else class="spotlight-fallback">{{ (spotlightShow?.title || data.name).slice(0, 1) }}</div>
            </div>
            <div class="spotlight-body">
              <p class="eyebrow">Entrada do recorte</p>
              <h2>{{ spotlightShow?.title || data.name }}</h2>
              <p v-if="spotlightShow" class="spotlight-copy">{{ spotlightShow.subtitle }}</p>
              <p v-if="spotlightShow" class="spotlight-copy">{{ spotlightShow.progressLabel }}</p>
              <p class="spotlight-note">
                {{ spotlightShow?.activityLabel || 'Ainda não há uma série ativa para abrir esse recorte.' }}
              </p>
            </div>
          </article>
        </component>
      </section>

      <ShowsLibraryGrid
        eyebrow="Recorte"
        :title="`Séries com ${data.name}`"
        :description="gridDescription"
        :summary="`${data.stats.showCount} séries no recorte e ${data.stats.watchedShowsCount} com episódios assistidos.`"
        :items="data.shows"
        empty-message="Nenhuma série ativa apareceu para esse termo."
      />
    </template>
  </main>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import ShowsLibraryGrid from '~/components/shows/ShowsLibraryGrid.vue'
import { useShowTermPageData } from '~/composables/useShowTermPageData'

const route = useRoute()
const { resolveMediaUrl } = useMediaUrl()
const kind = String(route.params.kind)
const termId = String(route.params.termId)
const slug = String(route.params.slug)
const { data, error, status } = await useShowTermPageData(kind, termId, slug)
const spotlightShow = computed(() => data.value?.shows[0] ?? null)
const spotlightImageUrl = computed(() => resolveMediaUrl(spotlightShow.value?.imageUrl ?? null))
const spotlightWrapper = computed(() => (spotlightShow.value?.href ? NuxtLink : 'div'))
const heroShellStyle = computed(() =>
  spotlightImageUrl.value
    ? {
        backgroundImage: `linear-gradient(180deg, rgba(255, 255, 255, 0.88), rgba(246, 243, 238, 0.97)), radial-gradient(circle at top right, rgba(230, 0, 35, 0.1), transparent 28%), url("${spotlightImageUrl.value}")`,
      }
    : undefined,
)
const heroIntro = computed(() =>
  data.value?.kind === 'GENRE'
    ? 'Um recorte amplo da biblioteca para percorrer histórias que compartilham a mesma classificação.'
    : 'Uma conexão mais específica entre séries que compartilham esta marcação no arquivo pessoal.',
)
const gridDescription = computed(() =>
  data.value?.kind === 'GENRE'
    ? 'A biblioteca de séries filtrada por um gênero específico.'
    : 'A biblioteca de séries filtrada por uma marcação mais específica.',
)

useHead(() => ({
  title: data.value ? `${data.value.name} · Séries · Media Pulse` : 'Termo · Séries · Media Pulse',
  meta: [
    { name: 'description', content: data.value ? `Séries ligadas ao termo ${data.value.name}.` : 'Termo de série.' },
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
.state-card p,
h1,
h2,
.intro,
.spotlight-copy,
.spotlight-note {
  margin: 0;
}
.state-card.error {
  color: #9e0a0a;
}
.term-hero {
  display: grid;
  grid-template-columns: minmax(0, 0.9fr) minmax(20rem, 1.1fr);
  gap: 24px;
  align-items: end;
  padding: clamp(24px, 4vw, 36px);
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  border-radius: 40px;
  background:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 28%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.95), rgba(246, 243, 238, 0.98));
  background-position: center;
  background-size: cover;
}
.copy,
.spotlight-body {
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
  font-size: clamp(3rem, 7vw, 5.8rem);
  line-height: 0.92;
  letter-spacing: -0.075em;
}
h2 {
  font-size: clamp(1.9rem, 3.4vw, 3rem);
  line-height: 0.98;
  letter-spacing: -0.04em;
}
.intro {
  max-width: 42rem;
  color: var(--base-color-text-secondary);
  line-height: 1.62;
}
.meta-list {
  display: flex;
  flex-wrap: wrap;
  gap: 10px;
}
.meta-pill {
  padding: 8px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
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
  border: 1px solid color-mix(in srgb, var(--base-color-border) 52%, white);
  border-radius: 40px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(246, 243, 238, 0.98));
}
.spotlight-poster {
  aspect-ratio: 0.82;
  overflow: hidden;
  border: 8px solid #fff;
  border-radius: 28px;
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
  font-size: 3rem;
  font-weight: 700;
}
.spotlight-copy {
  color: var(--base-color-text-secondary);
}
.spotlight-note {
  color: var(--base-color-text-muted);
  font-size: 0.88rem;
}
.back-link:focus-visible,
.spotlight-link:focus-visible {
  outline: 2px solid var(--base-color-focus, #435ee5);
  outline-offset: 2px;
}
@media (max-width: 980px) {
  .term-hero,
  .spotlight-card {
    grid-template-columns: 1fr;
  }
  .spotlight-poster {
    max-height: 34rem;
  }
}
@media (max-width: 576px) {
  .term-page {
    width: min(100% - 20px, 1480px);
    padding-top: 12px;
  }
  .term-hero {
    padding: 20px;
    border-radius: 28px;
  }
  h1 {
    font-size: clamp(2.6rem, 15vw, 4rem);
  }
}
</style>
