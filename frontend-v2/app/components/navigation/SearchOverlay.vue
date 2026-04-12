<template>
  <div v-if="open" class="search-overlay" @click.self="$emit('close')">
    <div class="search-panel">
      <div class="search-header">
        <input
          ref="inputRef"
          v-model="localQuery"
          class="search-input"
          type="text"
          placeholder="Buscar séries, filmes, livros, álbuns..."
        >
        <button type="button" class="close-button" @click="$emit('close')">
          Fechar
        </button>
      </div>

      <div class="search-body">
        <p v-if="!localQuery.trim()" class="search-hint">
          Use a busca para saltar direto para uma obra ou explorar por nome.
        </p>

        <p v-else-if="loading" class="search-hint">
          Procurando em toda a coleção...
        </p>

        <p v-else-if="error" class="search-hint error">
          Não foi possível concluir a busca agora.
        </p>

        <p v-else-if="results && results.total === 0" class="search-hint">
          Nenhum resultado apareceu para esse termo.
        </p>

        <div v-else-if="results" class="result-groups">
          <section v-for="group in results.groups" :key="group.id" class="result-group">
            <p class="group-title">{{ group.title }}</p>

            <component
              :is="item.href ? 'a' : 'div'"
              v-for="item in group.items"
              :key="item.id"
              :href="item.href || undefined"
              class="result-item"
              @click="handleItemClick"
            >
              <strong>{{ item.title }}</strong>
              <span>{{ item.subtitle }}</span>
            </component>
          </section>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import type { GlobalSearchData } from '~/types/search'

const props = defineProps<{
  open: boolean
  loading: boolean
  error: boolean
  query: string
  results: GlobalSearchData | null
}>()

const emit = defineEmits<{
  close: []
  search: [value: string]
}>()

const localQuery = ref(props.query)
const inputRef = ref<HTMLInputElement | null>(null)

watch(() => props.query, (value) => {
  localQuery.value = value
})

watch(localQuery, (value) => {
  emit('search', value)
})

watch(() => props.open, async (value) => {
  if (value) {
    await nextTick()
    inputRef.value?.focus()
  }
})

function handleItemClick() {
  emit('close')
}
</script>

<style scoped>
.search-overlay {
  position: fixed;
  inset: 0;
  z-index: 40;
  display: grid;
  place-items: start center;
  padding: 72px 16px 24px;
  background: rgba(33, 25, 34, 0.12);
  backdrop-filter: blur(8px);
}

.search-panel {
  width: min(980px, 100%);
  max-height: calc(100vh - 96px);
  overflow: auto;
  padding: 18px;
  border-radius: 32px;
  background: linear-gradient(180deg, rgba(255, 255, 255, 0.96), rgba(246, 243, 238, 0.98));
}

.search-header {
  display: grid;
  grid-template-columns: minmax(0, 1fr) auto;
  gap: 12px;
}

.search-input {
  width: 100%;
  padding: 14px 18px;
  border: 1px solid var(--base-color-border);
  border-radius: 18px;
  background: white;
}

.close-button {
  padding: 12px 16px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-surface-warm);
  cursor: pointer;
}

.search-body {
  display: grid;
  gap: 22px;
  margin-top: 18px;
}

.search-hint {
  margin: 0;
  color: var(--base-color-text-secondary);
}

.search-hint.error {
  color: #7a1414;
}

.result-groups {
  display: grid;
  gap: 18px;
}

.result-group {
  display: grid;
  gap: 10px;
}

.group-title {
  margin: 0;
  color: var(--base-color-brand-red);
  font-size: 0.76rem;
  font-weight: 700;
  text-transform: uppercase;
  letter-spacing: 0.08em;
}

.result-item {
  display: grid;
  gap: 4px;
  padding: 14px 16px;
  border-radius: 18px;
  background: rgba(255, 255, 255, 0.72);
  color: inherit;
  text-decoration: none;
}

.result-item span {
  color: var(--base-color-text-secondary);
  font-size: 0.9rem;
}

@media (max-width: 720px) {
  .search-header {
    grid-template-columns: 1fr;
  }
}
</style>
