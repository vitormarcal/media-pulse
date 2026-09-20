<template>
  <section v-if="editions.length || readingEdition" class="editions-panel" aria-label="Edição">
    <div v-if="readingEdition" class="reading-edition">
      <h2>Edição da leitura</h2>
      <p>{{ readingEdition.title }}</p>
      <p v-if="readingEdition.meta.length" class="edition-meta">{{ readingEdition.meta.join(' · ') }}</p>
    </div>
    <details v-if="otherEditions.length">
      <summary>{{ readingEdition ? 'Ver outras edições' : 'Ver edições' }}</summary>
      <div class="edition-list">
        <div v-for="edition in otherEditions" :key="edition.id">
          <p>{{ edition.title }}</p>
          <p v-if="edition.meta.length" class="edition-meta">{{ edition.meta.join(' · ') }}</p>
        </div>
      </div>
    </details>
  </section>
</template>
<script setup lang="ts">
import type { BookPageData } from '~/types/books'
const props = defineProps<{
  editions: BookPageData['editions']
  readingEdition: BookPageData['readingEdition']
}>()
const otherEditions = computed(() => props.editions.filter((edition) => edition.id !== props.readingEdition?.id))
</script>
<style scoped>
.editions-panel,
.reading-edition,
.edition-list {
  display: grid;
  gap: 12px;
}
.editions-panel {
  padding-top: 24px;
  border-top: 1px solid var(--base-color-border);
}
h2 {
  margin: 0;
  font-size: 1rem;
}
p {
  margin: 0;
}
.edition-meta,
summary {
  color: var(--base-color-text-secondary);
  font-size: 0.88rem;
}
summary {
  cursor: pointer;
  width: fit-content;
  padding: 8px 0;
}
summary:focus-visible {
  outline: 2px solid var(--base-color-focus);
  outline-offset: 4px;
}
.edition-list {
  margin-top: 12px;
  gap: 20px;
}
.edition-meta {
  margin-top: 4px;
}
</style>
