<template>
  <main class="book-groups">
    <header class="group-hero group-index-hero">
      <div class="group-hero-copy">
        <NuxtLink class="group-back" to="/books">Voltar para livros</NuxtLink>
        <p class="group-eyebrow">{{ kind === 'series' ? 'Séries' : 'Universos' }}</p>
        <h1>{{ title }}</h1>
        <nav class="group-actions" aria-label="Organização de livros">
          <NuxtLink :to="kind === 'series' ? '/books/universes' : '/books/series'">{{
            kind === 'series' ? 'Explorar universos' : 'Explorar séries de livros'
          }}</NuxtLink>
        </nav>
      </div>
      <form v-owner class="group-create-card" @submit.prevent="create">
        <h2>{{ kind === 'series' ? 'Nova série' : 'Novo universo' }}</h2>
        <label
          >Nome<input
            v-model="name"
            required
            maxlength="300"
            :disabled="busy"
            :placeholder="kind === 'series' ? 'Nome da série' : 'Nome do universo'"
        /></label>
        <button class="group-primary" :disabled="busy || !name.trim()" type="submit">
          {{ busy ? 'Criando...' : kind === 'series' ? 'Criar série' : 'Criar universo' }}
        </button>
        <p v-if="message" role="alert" class="group-error">{{ message }}</p>
      </form>
    </header>
    <section class="group-section">
      <div class="group-section-heading">
        <h2>{{ kind === 'series' ? 'Suas séries' : 'Seus universos' }}</h2>
        <span class="group-muted">{{ groups?.length || 0 }} {{ kind === 'series' ? 'séries' : 'universos' }}</span>
      </div>
      <p v-if="status === 'pending'" class="group-state">Carregando...</p>
      <div v-else-if="error" class="group-state group-error" role="alert">
        Não foi possível carregar. <button @click="refresh()">Tentar novamente</button>
      </div>
      <p v-else-if="!groups?.length" class="group-state">
        {{ kind === 'series' ? 'Nenhuma série cadastrada.' : 'Nenhum universo cadastrado.' }}
      </p>
      <div v-else class="group-grid">
        <BookGroupCard v-for="group in groups" :key="group.id" :group="group" :kind="kind" />
      </div>
    </section>
  </main>
</template>
<script setup lang="ts">
import type { BookGroup, BookGroupDetails, BookGroupKind } from '~/types/book-series'
import { bookGroupError, useBookGroupsApi } from '~/composables/useBookGroupsApi'
import BookGroupCard from '~/components/books/BookGroupCard.vue'
import '~/assets/css/book-groups.css'
const props = defineProps<{ kind: BookGroupKind }>()
const api = useBookGroupsApi()
const title = computed(() => (props.kind === 'series' ? 'Séries de livros' : 'Universos'))
const {
  data: groups,
  error,
  status,
  refresh,
} = await useAsyncData(`book-groups-${props.kind}`, () => api<BookGroup[]>(`/${props.kind}`))
const name = ref('')
const busy = ref(false)
const message = ref('')
async function create() {
  if (busy.value) return
  busy.value = true
  message.value = ''
  try {
    const result = await api<BookGroupDetails>(`/${props.kind}`, 'POST', { name: name.value })
    await navigateTo(`/books/${props.kind}/${result.id}`)
  } catch (cause) {
    message.value = bookGroupError(cause)
  } finally {
    busy.value = false
  }
}
useHead(() => ({ title: `${title.value} · Media Pulse` }))
</script>
