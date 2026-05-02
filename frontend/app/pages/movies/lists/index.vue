<template>
  <main class="lists-index-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a estante de listas...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar as listas manuais.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <MoviesLibraryHero
        :title="data.hero.title"
        :intro="data.hero.intro"
        :back-link="data.hero.backLink"
        :back-label="data.hero.backLabel"
        :accent-link="data.hero.accentLink"
        :accent-label="data.hero.accentLabel"
        :spotlight="data.hero.spotlight"
      />

      <section class="lists-section">
        <SectionHeading
          eyebrow="Estante manual"
          title="Todos os recortes já abertos"
          description="Cada lista manual funciona como uma porta de entrada menor, mais intencional e menos enciclopédica para a filmoteca."
          :summary="data.summary"
        />

        <p v-if="!data.items.length" class="quiet-empty">Nenhuma lista manual foi criada ainda.</p>

        <div v-else class="lists-masonry">
          <article v-for="item in data.items" :key="item.id" class="list-card" :style="cardShellStyle(item)">
            <NuxtLink class="card-link" :to="item.href">
              <div class="poster-mosaic" :class="[previewClass(item.previewMovies.length), posterTone(item.listId)]">
                <template v-if="item.previewMovies.length">
                  <div v-for="preview in item.previewMovies.slice(0, 3)" :key="preview.id" class="poster-tile">
                    <img
                      v-if="resolveMediaUrl(preview.imageUrl)"
                      :src="resolveMediaUrl(preview.imageUrl)"
                      :alt="preview.title"
                    />
                    <div v-else class="poster-fallback">{{ preview.title.slice(0, 1) }}</div>
                  </div>
                </template>

                <div v-else class="poster-fallback poster-fallback--large">{{ item.name.slice(0, 1) }}</div>
              </div>

              <div class="card-copy">
                <p class="card-kicker">Lista manual</p>
                <h2>{{ item.name }}</h2>
                <p class="card-description">{{ item.description || fallbackDescription(item.itemCount) }}</p>
              </div>

              <div class="card-footer">
                <span class="meta-pill">{{ item.itemCount }} filmes</span>
                <span class="meta-pill meta-pill--muted">Curadoria própria</span>
                <span class="open-note">Abrir recorte</span>
              </div>
            </NuxtLink>
          </article>
        </div>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import { NuxtLink } from '#components'
import SectionHeading from '~/components/home/SectionHeading.vue'
import MoviesLibraryHero from '~/components/movies/MoviesLibraryHero.vue'
import { useMovieListsIndexPageData } from '~/composables/useMovieListPageData'

const { resolveMediaUrl } = useMediaUrl()
const { data, error, status } = await useMovieListsIndexPageData()

function previewClass(count: number) {
  if (count <= 0) return 'poster-mosaic--empty'
  if (count === 1) return 'poster-mosaic--single'
  if (count === 2) return 'poster-mosaic--double'
  return 'poster-mosaic--triple'
}

function posterTone(listId: number) {
  return `poster-mosaic--tone-${listId % 4}`
}

function cardShellStyle(item: (typeof data.value.items)[number]) {
  const heroImageUrl = resolveMediaUrl(item.coverImageUrl ?? item.previewMovies[0]?.imageUrl ?? null)
  if (!heroImageUrl) return undefined

  return {
    backgroundImage: `linear-gradient(180deg, rgba(255, 255, 255, 0.9), rgba(246, 243, 238, 0.97)), radial-gradient(circle at top right, rgba(230, 0, 35, 0.08), transparent 30%), url("${heroImageUrl}")`,
  }
}

function fallbackDescription(itemCount: number) {
  return itemCount > 1
    ? `Um recorte manual com ${itemCount} filmes para navegar fora da biblioteca geral.`
    : 'Um recorte manual enxuto, pensado como porta de entrada para revisitas e descobertas.'
}

useHead(() => ({
  title: 'Listas manuais · Filmes · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Estante de listas manuais de filmes no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.lists-index-page {
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

.lists-section {
  display: grid;
  gap: 24px;
}

.lists-masonry {
  column-count: 3;
  column-gap: 20px;
}

.list-card {
  break-inside: avoid;
  margin-bottom: 20px;
  border-radius: 32px;
  overflow: hidden;
  border: 1px solid color-mix(in srgb, var(--base-color-border) 55%, white);
  background-image:
    radial-gradient(circle at top right, rgba(230, 0, 35, 0.06), transparent 30%),
    linear-gradient(180deg, rgba(255, 255, 255, 0.97), rgba(246, 243, 238, 0.98));
  background-size: cover;
  background-position: center;
}

.card-link {
  display: grid;
  gap: 16px;
  color: inherit;
}

.poster-mosaic {
  display: grid;
  gap: 6px;
  min-height: 13rem;
  padding: 10px;
  background: color-mix(in srgb, var(--base-color-surface-soft) 88%, white);
}

.poster-mosaic--single,
.poster-mosaic--empty {
  grid-template-columns: 1fr;
}

.poster-mosaic--double {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.poster-mosaic--triple {
  grid-template-columns: 1.15fr 0.85fr;
  grid-template-rows: repeat(2, minmax(0, 1fr));
}

.poster-mosaic--triple .poster-tile:first-child {
  grid-row: span 2;
}

.poster-mosaic--tone-0 {
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.92), rgba(246, 243, 238, 1));
}

.poster-mosaic--tone-1 {
  background: linear-gradient(180deg, rgba(250, 245, 238, 0.95), rgba(240, 234, 225, 0.98));
}

.poster-mosaic--tone-2 {
  background: linear-gradient(180deg, rgba(245, 244, 238, 0.96), rgba(236, 235, 227, 0.98));
}

.poster-mosaic--tone-3 {
  background: linear-gradient(180deg, rgba(247, 242, 236, 0.96), rgba(242, 236, 228, 0.98));
}

.poster-tile,
.poster-fallback {
  overflow: hidden;
  border-radius: 22px;
  border: 6px solid #fff;
  background: var(--base-color-surface-strong);
}

.poster-tile img,
.poster-fallback {
  width: 100%;
  height: 100%;
  object-fit: cover;
}

.poster-fallback {
  display: grid;
  place-items: center;
  color: var(--base-color-text-secondary);
  font-size: 2.6rem;
  font-weight: 700;
}

.poster-fallback--large {
  min-height: 13rem;
}

.card-copy,
.card-footer {
  padding: 0 22px;
}

.card-copy {
  display: grid;
  gap: 8px;
}

.card-kicker {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.74rem;
  font-weight: 700;
  letter-spacing: 0.09em;
  text-transform: uppercase;
}

h2,
.card-description,
.quiet-empty {
  margin: 0;
}

h2 {
  font-size: 1.3rem;
  line-height: 1.04;
  letter-spacing: -0.04em;
}

.card-description,
.quiet-empty {
  color: var(--base-color-text-secondary);
  line-height: 1.55;
}

.card-footer {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
  align-items: center;
  padding-bottom: 22px;
}

.meta-pill {
  padding: 8px 12px;
  border-radius: 16px;
  background: color-mix(in srgb, var(--base-color-surface-wash) 72%, white);
  color: var(--base-color-text-primary);
  font-size: 0.78rem;
}

.meta-pill--muted {
  color: var(--base-color-text-secondary);
}

.open-note {
  margin-left: auto;
  color: var(--base-color-text-secondary);
  font-size: 0.8rem;
  font-weight: 700;
}

.quiet-empty {
  font-size: 0.92rem;
}

@media (max-width: 1180px) {
  .lists-masonry {
    column-count: 2;
  }
}

@media (max-width: 720px) {
  .lists-index-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }

  .lists-masonry {
    column-count: 1;
  }
}

@media (max-width: 560px) {
  .open-note {
    margin-left: 0;
  }
}
</style>
