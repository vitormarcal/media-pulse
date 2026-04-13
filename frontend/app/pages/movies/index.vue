<template>
  <main class="movies-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página de filmes...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível montar a página de filmes com os dados atuais.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <MoviesCollectionHero
        :title="data.hero.title"
        :intro="data.hero.intro"
        :lead="data.hero.lead"
        :supporting="data.hero.supporting"
      />

      <section class="movies-section">
        <SectionHeading
          eyebrow="Em circulação"
          title="Os filmes que ainda seguem reverberando"
          description="Os títulos que passaram recentemente e ainda fazem sentido manter por perto antes de virar biblioteca."
          summary="Mais uma mesa de retorno rápido do que um arquivo completo."
        />

        <div class="strip-grid">
          <MediaStripCard
            v-for="item in data.featuredSessions"
            :key="item.id"
            :item="item"
            variant="large"
          />
        </div>
      </section>

      <MoviesCollectionContext
        :eyebrow="data.context.eyebrow"
        :title="data.context.title"
        :description="data.context.description"
        :summary="data.context.summary"
        :metrics="data.context.metrics"
      />

      <section id="movies-recent" class="movies-section">
        <SectionHeading
          eyebrow="Sessões recentes"
          title="O que acabou de voltar para a tela"
          description="Uma parede curta dos filmes mais próximos, organizada para reconhecimento rápido e navegação direta."
          summary="A cronologia curta importa mais do que qualquer tentativa de taxonomia aqui."
        />

        <div class="masonry-grid">
          <MediaPosterCard
            v-for="(item, index) in data.recentMoments"
            :key="item.id"
            :item="item"
            :variant="cardVariant(index)"
          />
        </div>
      </section>
    </template>
  </main>
</template>

<script setup lang="ts">
import MediaPosterCard from '~/components/home/MediaPosterCard.vue'
import MediaStripCard from '~/components/home/MediaStripCard.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'
import MoviesCollectionContext from '~/components/movies/MoviesCollectionContext.vue'
import MoviesCollectionHero from '~/components/movies/MoviesCollectionHero.vue'
import { useMoviesCollectionData } from '~/composables/useMoviesCollectionData'

const { data, error, status } = await useMoviesCollectionData()

function cardVariant(index: number) {
  const pattern = ['feature', 'compact', 'tall', 'standard', 'compact', 'tall'] as const
  return pattern[index % pattern.length]
}

useHead(() => ({
  title: 'Filmes · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Recorte editorial dos filmes recentes e do histórico de sessões no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.movies-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.movies-section,
.strip-grid {
  display: grid;
  gap: 24px;
}

.strip-grid {
  grid-template-columns: repeat(2, minmax(0, 1fr));
}

.masonry-grid {
  column-count: 4;
  column-gap: 20px;
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

@media (max-width: 1280px) {
  .masonry-grid {
    column-count: 3;
  }
}

@media (max-width: 900px) {
  .strip-grid {
    grid-template-columns: 1fr;
  }

  .masonry-grid {
    column-count: 2;
  }
}

@media (max-width: 720px) {
  .movies-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}

@media (max-width: 520px) {
  .masonry-grid {
    column-count: 1;
  }
}
</style>
