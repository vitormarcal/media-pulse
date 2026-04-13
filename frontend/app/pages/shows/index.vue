<template>
  <main class="shows-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página de séries...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível montar a página de séries com os dados atuais.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <ShowsCollectionHero
        :title="data.hero.title"
        :intro="data.hero.intro"
        :lead="data.hero.lead"
        :supporting="data.hero.supporting"
      />

      <section class="shows-section">
        <SectionHeading
          eyebrow="Em curso"
          title="As séries que ainda seguem abertas"
          description="O ponto onde cada uma ficou, para voltar sem procurar demais."
          summary="Mais mesa de acompanhamento do que arquivo completo."
        />

        <div class="strip-grid">
          <MediaStripCard
            v-for="item in data.inProgress"
            :key="item.id"
            :item="item"
            variant="large"
          />
        </div>
      </section>

      <ShowsCollectionContext
        :eyebrow="data.context.eyebrow"
        :title="data.context.title"
        :description="data.context.description"
        :summary="data.context.summary"
        :metrics="data.context.metrics"
      />

      <section class="shows-section">
        <SectionHeading
          eyebrow="Mais recente"
          title="O que acabou de passar pela tela"
          description="Os últimos registros em ordem curta, do jeito que a memória recente costuma funcionar."
          summary="Sem tentar explicar demais: só o fluxo do que andou por último."
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
import ShowsCollectionContext from '~/components/shows/ShowsCollectionContext.vue'
import ShowsCollectionHero from '~/components/shows/ShowsCollectionHero.vue'
import { useShowsCollectionData } from '~/composables/useShowsCollectionData'

const { data, error, status } = await useShowsCollectionData()

function cardVariant(index: number) {
  const pattern = ['feature', 'compact', 'tall', 'standard', 'compact', 'tall'] as const
  return pattern[index % pattern.length]
}

useHead(() => ({
  title: 'Séries · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Recorte editorial das séries em andamento e do fluxo recente no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.shows-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.shows-section,
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
  .shows-page {
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
