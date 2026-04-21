<template>
  <main class="author-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página do autor...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar este autor.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <AuthorPageHero :title="data.name" :cover-url="data.coverUrl" :hero-meta="data.heroMeta" />

      <AuthorContextPanel :stats="data.stats" />

      <BooksLibraryGrid
        eyebrow="Livros"
        title="Os livros que trazem essa assinatura"
        description="Os títulos que já apareceram no arquivo e sustentam a entrada por autoria."
        summary="Autor como aprofundamento, livro como unidade principal de navegação."
        :items="data.books"
        empty-message="Nenhum livro apareceu para esse autor."
      />

      <BookReadTimeline :reads="data.recentReads" />
    </template>
  </main>
</template>

<script setup lang="ts">
import AuthorContextPanel from '~/components/books/AuthorContextPanel.vue'
import AuthorPageHero from '~/components/books/AuthorPageHero.vue'
import BookReadTimeline from '~/components/books/BookReadTimeline.vue'
import BooksLibraryGrid from '~/components/books/BooksLibraryGrid.vue'
import { useAuthorPageData } from '~/composables/useAuthorPageData'

const route = useRoute()
const id = computed(() => String(route.params.id))

const { data, error, status } = await useAuthorPageData(id.value)

useHead(() => ({
  title: data.value ? `${data.value.name} · Media Pulse` : 'Autor · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value
        ? `${data.value.name} dentro do seu arquivo de livros.`
        : 'Página interna de autor no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.author-page {
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

@media (max-width: 720px) {
  .author-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
