<template>
  <main class="books-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página de livros...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível montar a página de livros com os dados atuais.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <BooksCollectionHero
        :title="data.hero.title"
        :intro="data.hero.intro"
        :lead="data.hero.lead"
        :supporting="data.hero.supporting"
      />

      <section class="books-section">
        <SectionHeading
          eyebrow="Em leitura"
          title="Os livros que ainda seguem abertos"
          description="Os que continuam pedindo mais algumas páginas e ainda merecem ficar na superfície."
          summary="Mais uma mesa de retorno do que uma estante completa."
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

      <BooksCollectionContext
        :eyebrow="data.context.eyebrow"
        :title="data.context.title"
        :description="data.context.description"
        :summary="data.context.summary"
        :metrics="data.context.metrics"
      />

      <section id="books-finished" class="books-section">
        <SectionHeading
          eyebrow="Fechados por último"
          title="O que acabou de sair da pilha mental"
          description="Uma parede curta dos livros concluídos recentemente, organizada para reconhecimento rápido e navegação direta."
          summary="Aqui o fechamento recente vale mais do que qualquer taxonomia de estante."
        />

        <div class="masonry-grid">
          <MediaPosterCard
            v-for="(item, index) in data.recentFinishes"
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
import BooksCollectionContext from '~/components/books/BooksCollectionContext.vue'
import BooksCollectionHero from '~/components/books/BooksCollectionHero.vue'
import MediaPosterCard from '~/components/home/MediaPosterCard.vue'
import MediaStripCard from '~/components/home/MediaStripCard.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'
import { useBooksCollectionData } from '~/composables/useBooksCollectionData'

const { data, error, status } = await useBooksCollectionData()

function cardVariant(index: number) {
  const pattern = ['feature', 'compact', 'tall', 'standard', 'compact', 'tall'] as const
  return pattern[index % pattern.length]
}

useHead(() => ({
  title: 'Livros · Media Pulse',
  meta: [
    {
      name: 'description',
      content: 'Recorte editorial das leituras em curso e dos livros concluídos recentemente no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.books-page {
  display: grid;
  gap: var(--sema-space-section);
  width: min(1480px, calc(100vw - 32px));
  margin: 0 auto;
  padding: 28px 0 84px;
}

.books-section,
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
  .books-page {
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
