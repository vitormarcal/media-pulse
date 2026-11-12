<template>
  <section v-if="editions.length || readingEdition" class="editions-panel" aria-label="Edição">
    <div v-if="readingEdition" class="reading-edition">
      <h3>Edição da leitura mais recente</h3>
      <p>{{ readingEdition.title }}</p>
      <p v-if="readingEdition.meta.length" class="edition-meta">{{ readingEdition.meta.join(' · ') }}</p>
      <details v-if="readingEdition.isbn10 || readingEdition.isbn13" class="edition-isbn">
        <summary>Ver ISBN</summary>
        <dl>
          <div v-if="readingEdition.isbn10">
            <dt>ISBN-10</dt>
            <dd>{{ readingEdition.isbn10 }}</dd>
          </div>
          <div v-if="readingEdition.isbn13">
            <dt>ISBN-13</dt>
            <dd>{{ readingEdition.isbn13 }}</dd>
          </div>
        </dl>
      </details>
    </div>
    <details v-if="otherEditions.length">
      <summary>{{ readingEdition ? 'Ver outras edições' : 'Ver edições' }}</summary>
      <div class="edition-list">
        <div v-for="edition in otherEditions" :key="edition.id">
          <p>{{ edition.title }}</p>
          <p v-if="edition.meta.length" class="edition-meta">{{ edition.meta.join(' · ') }}</p>
          <dl v-if="edition.isbn10 || edition.isbn13">
            <div v-if="edition.isbn10">
              <dt>ISBN-10</dt>
              <dd>{{ edition.isbn10 }}</dd>
            </div>
            <div v-if="edition.isbn13">
              <dt>ISBN-13</dt>
              <dd>{{ edition.isbn13 }}</dd>
            </div>
          </dl>
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
h3 {
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

dl {
  display: grid;
  gap: 8px;
  margin: 12px 0 0;
  font-size: 0.82rem;
}
dl div {
  display: flex;
  flex-wrap: wrap;
  gap: 8px;
}
dt {
  color: var(--base-color-text-secondary);
}
dd {
  margin: 0;
  overflow-wrap: anywhere;
}
</style>
