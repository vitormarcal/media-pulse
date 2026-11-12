<template>
  <p class="author-context">
    <span>{{ stats.booksCount }} {{ stats.booksCount === 1 ? 'livro no acervo' : 'livros no acervo' }}</span>
    <span v-if="stats.finishedCount"
      >{{ stats.finishedCount }} {{ stats.finishedCount === 1 ? 'leitura concluída' : 'leituras concluídas' }}</span
    >
    <span v-if="stats.currentlyReadingCount">{{ stats.currentlyReadingCount }} em leitura</span>
  </p>
</template>

<script setup lang="ts">
import type { AuthorPageData } from '~/types/books'
defineProps<{ stats: AuthorPageData['stats'] }>()
</script>

<style scoped>
.author-context {
  display: flex;
  flex-wrap: wrap;
  gap: 8px 20px;
  margin: 20px 0 0;
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
  line-height: 1.6;
}
</style>
