<template>
  <main class="book-groups">
    <nav aria-label="Livros">
      <NuxtLink to="/books">Livros</NuxtLink
      ><NuxtLink :to="`/books/${kind}`">{{ kind === 'series' ? 'Séries' : 'Universos' }}</NuxtLink>
    </nav>
    <p v-if="status === 'pending'">Carregando...</p>
    <div v-else-if="error" role="alert">
      Não foi possível carregar. <button @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="group">
      <h1>{{ group.name }}</h1>
      <nav v-if="group.universes.length" aria-label="Universos da série">
        <NuxtLink v-for="universe in group.universes" :key="universe.id" :to="`/books/universes/${universe.id}`">{{
          universe.name
        }}</NuxtLink>
      </nav>
      <details @toggle="loadChoices">
        <summary>{{ kind === 'series' ? 'Gerenciar série' : 'Gerenciar universo' }}</summary>
        <div class="group-management">
          <form @submit.prevent="rename">
            <label>Nome<input v-model="name" required maxlength="300" :disabled="busy" /></label
            ><button type="submit" :disabled="busy">Salvar nome</button>
          </form>
          <form @submit.prevent="add">
            <template v-if="kind === 'series'"
              ><label>Buscar livro no acervo<input v-model="query" type="search" :disabled="busy" /></label
              ><button :disabled="busy || !query.trim()" type="button" @click="search">Buscar</button></template
            >
            <label
              >{{ kind === 'series' ? 'Livro' : 'Série'
              }}<select v-model="selected" required :disabled="busy">
                <option value="">Selecionar</option>
                <option v-for="item in choices" :key="item.id" :value="item.id">{{ item.name }}</option>
              </select></label
            >
            <button type="submit" :disabled="busy || !selected">Adicionar ao final</button>
          </form>
          <form v-if="kind === 'series'" @submit.prevent="linkUniverse">
            <label
              >Universo<select v-model="universeId" required :disabled="busy">
                <option value="">Selecionar</option>
                <option v-for="universe in universes" :key="universe.id" :value="universe.id">
                  {{ universe.name }}
                </option>
              </select></label
            ><button :disabled="busy || !universeId" type="submit">Associar ao universo</button
            ><NuxtLink to="/books/universes">Criar universo</NuxtLink>
          </form>
          <label class="group-check"
            ><input v-model="confirmDelete" type="checkbox" :disabled="busy" />Confirmar exclusão
            {{ kind === 'series' ? 'da série' : 'do universo' }}</label
          >
          <button :disabled="busy || !confirmDelete" @click="removeGroup">
            Excluir {{ kind === 'series' ? 'série' : 'universo' }}
          </button>
        </div>
      </details>
      <p v-if="message" role="status">{{ message }}</p>
      <h2>Ordem de leitura</h2>
      <p v-if="!group.items.length">{{ kind === 'series' ? 'Nenhum livro associado.' : 'Nenhuma série associada.' }}</p>
      <ol class="group-members">
        <li v-for="(item, index) in group.items" :key="item.id" class="group-member">
          <NuxtLink
            class="group-member-link"
            :to="kind === 'series' ? `/books/${item.slug}` : `/books/series/${item.id}`"
            ><MediaImage v-if="item.coverUrl" :src="item.coverUrl" alt="" class="group-cover" /><strong>{{
              item.name
            }}</strong></NuxtLink
          >
          <div class="group-actions">
            <button
              :disabled="busy || index === 0"
              :aria-label="`Mover ${item.name} para cima`"
              @click="move(index, -1)"
            >
              ↑ Subir</button
            ><button
              :disabled="busy || index === group.items.length - 1"
              :aria-label="`Mover ${item.name} para baixo`"
              @click="move(index, 1)"
            >
              ↓ Descer</button
            ><button
              :disabled="busy"
              :aria-label="`Remover ${item.name} deste agrupamento`"
              @click="change(`/${kind}/${id}/items/${item.id}`, 'DELETE')"
            >
              Remover vínculo
            </button>
          </div>
        </li>
      </ol>
    </template>
  </main>
</template>
<script setup lang="ts">
import type { BookGroup, BookGroupDetails, BookGroupKind } from '~/types/book-series'
import { bookGroupError, useBookGroupsApi } from '~/composables/useBookGroupsApi'
import '~/assets/css/book-groups.css'
const props = defineProps<{ kind: BookGroupKind; id: string }>()
const api = useBookGroupsApi()
const {
  data: group,
  error,
  status,
  refresh,
} = await useAsyncData(`book-group-${props.kind}-${props.id}`, () =>
  api<BookGroupDetails>(`/${props.kind}/${props.id}`),
)
const name = ref(group.value?.name || '')
watch(group, (value) => {
  if (value) name.value = value.name
})
const busy = ref(false)
const message = ref('')
const confirmDelete = ref(false)
const query = ref('')
const selected = ref<number | ''>('')
const universeId = ref<number | ''>('')
const choices = ref<{ id: number; name: string }[]>([])
const universes = ref<BookGroup[]>([])
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  message.value = ''
  try {
    await action()
  } catch (cause) {
    message.value = bookGroupError(cause)
  } finally {
    busy.value = false
  }
}
async function loadChoices(event: Event) {
  if (!(event.target as HTMLDetailsElement).open) return
  await run(async () => {
    if (props.kind === 'universes') choices.value = await api<BookGroup[]>('/series')
    else universes.value = await api<BookGroup[]>('/universes')
  })
}
async function search() {
  await run(async () => {
    const result = await api<{ books: { bookId: number; title: string }[] }>(
      `/search?q=${encodeURIComponent(query.value)}&limit=50`,
    )
    choices.value = result.books.map((item) => ({ id: item.bookId, name: item.title }))
    selected.value = ''
    if (!choices.value.length) message.value = 'Nenhum livro encontrado no acervo.'
  })
}
async function change(path: string, method: 'PUT' | 'POST' | 'DELETE', body?: object) {
  await run(async () => {
    group.value = await api<BookGroupDetails>(path, method, body)
    message.value = 'Alteração salva.'
  })
}
function rename() {
  return change(`/${props.kind}/${props.id}`, 'PUT', { name: name.value })
}
function add() {
  return change(`/${props.kind}/${props.id}/items/${selected.value}`, 'POST')
}
async function linkUniverse() {
  await run(async () => {
    await api(`/universes/${universeId.value}/items/${props.id}`, 'POST')
    await refresh()
    message.value = 'Universo associado.'
  })
}
function move(index: number, direction: number) {
  const ids = group.value?.items.map((item) => item.id) || []
  const next = index + direction
  if (next < 0 || next >= ids.length) return
  const current = ids[index]!
  ids[index] = ids[next]!
  ids[next] = current
  return change(`/${props.kind}/${props.id}/order`, 'PUT', { ids })
}
async function removeGroup() {
  if (!confirmDelete.value) return
  await run(async () => {
    await api(`/${props.kind}/${props.id}`, 'DELETE')
    await navigateTo(`/books/${props.kind}`)
  })
}
useHead(() => ({ title: `${group.value?.name || 'Livros'} · Media Pulse` }))
</script>
