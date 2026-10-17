<template>
  <main class="book-groups">
    <nav aria-label="Livros">
      <NuxtLink to="/books">Livros</NuxtLink><NuxtLink to="/books/series">Séries</NuxtLink
      ><NuxtLink to="/books/universes">Universos</NuxtLink>
    </nav>
    <h1>{{ title }}</h1>
    <details>
      <summary>{{ kind === 'series' ? 'Criar série' : 'Criar universo' }}</summary>
      <form @submit.prevent="create">
        <label>Nome<input v-model="name" required maxlength="300" :disabled="busy" /></label
        ><button :disabled="busy" type="submit">Criar</button>
      </form>
    </details>
    <p v-if="message" role="alert">{{ message }}</p>
    <p v-if="status === 'pending'">Carregando...</p>
    <div v-else-if="error" role="alert">
      Não foi possível carregar. <button @click="refresh()">Tentar novamente</button>
    </div>
    <p v-else-if="!groups?.length">
      {{ kind === 'series' ? 'Nenhuma série cadastrada.' : 'Nenhum universo cadastrado.' }}
    </p>
    <div class="group-grid">
      <NuxtLink v-for="group in groups" :key="group.id" class="group-card" :to="`/books/${kind}/${group.id}`"
        ><strong>{{ group.name }}</strong
        ><span>{{ group.itemCount }} {{ kind === 'series' ? 'livros' : 'séries' }}</span></NuxtLink
      >
    </div>
  </main>
</template>
<script setup lang="ts">
import type { BookGroup, BookGroupDetails, BookGroupKind } from '~/types/book-series'
import { bookGroupError, useBookGroupsApi } from '~/composables/useBookGroupsApi'
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
