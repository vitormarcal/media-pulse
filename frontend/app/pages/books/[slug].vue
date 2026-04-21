<template>
  <main class="book-page">
    <div v-if="status === 'pending'" class="state-card">
      <p>Montando a página do livro...</p>
    </div>

    <div v-else-if="error" class="state-card error">
      <p>Não foi possível carregar este livro.</p>
      <pre>{{ error.message }}</pre>
    </div>

    <template v-else-if="data">
      <BookPageHero
        :title="data.title"
        :authors="data.authors"
        :subtitle="data.subtitle"
        :description="data.description"
        :cover-url="data.coverUrl"
        :hero-meta="data.heroMeta"
      />

      <BookContextPanel :stats="data.stats" :editions="data.editions" />

      <BookReviewPanel v-if="data.reviewRaw" :review="data.reviewRaw" :reviewed-at="data.reviewedAt" />

      <BookReadTimeline :reads="data.recentReads" />
    </template>
  </main>
</template>

<script setup lang="ts">
import BookContextPanel from '~/components/books/BookContextPanel.vue'
import BookPageHero from '~/components/books/BookPageHero.vue'
import BookReadTimeline from '~/components/books/BookReadTimeline.vue'
import BookReviewPanel from '~/components/books/BookReviewPanel.vue'
import { useBookPageData } from '~/composables/useBookPageData'

const route = useRoute()
const slug = computed(() => String(route.params.slug))

const { data, error, status } = await useBookPageData(slug.value)

useHead(() => ({
  title: data.value ? `${data.value.title} · Media Pulse` : 'Livro · Media Pulse',
  meta: [
    {
      name: 'description',
      content: data.value?.description || 'Página interna de livro no Media Pulse.',
    },
  ],
}))
</script>

<style scoped>
.book-page {
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
  .book-page {
    width: min(100vw - 20px, 1480px);
    padding: 20px 0 64px;
  }
}
</style>
