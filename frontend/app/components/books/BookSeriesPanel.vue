<template>
  <section class="book-groups group-panel" aria-labelledby="book-series-heading">
    <h2 id="book-series-heading">Séries</h2>
    <p v-if="status === 'pending'">Carregando séries...</p>
    <p v-else-if="error" role="alert">
      Não foi possível carregar as séries. <button @click="refresh()">Tentar novamente</button>
    </p>
    <nav v-else-if="memberships?.length" aria-label="Séries deste livro">
      <NuxtLink v-for="series in memberships" :key="series.id" :to="`/books/series/${series.id}`">{{
        series.name
      }}</NuxtLink>
    </nav>
    <p v-else>Nenhuma série associada.</p>
    <details @toggle="loadSeries">
      <summary>Gerenciar séries</summary>
      <div class="group-management">
        <form @submit.prevent="associate">
          <label
            >Série<select v-model="selectedSeries" required :disabled="busy">
              <option value="">Selecionar</option>
              <option v-for="series in allSeries" :key="series.id" :value="series.id">{{ series.name }}</option>
            </select></label
          ><button type="submit" :disabled="busy || !selectedSeries">Associar livro</button>
        </form>
        <form @submit.prevent="create">
          <label>Nome da nova série<input v-model="newName" required maxlength="300" :disabled="busy" /></label
          ><button type="submit" :disabled="busy">Criar série e associar livro</button>
        </form>
        <div v-for="series in memberships" :key="series.id">
          <button :disabled="busy" @click="unlink(series.id)">Remover vínculo com {{ series.name }}</button>
        </div>
        <button :disabled="busy" @click="preview">{{ busy ? 'Aguarde...' : 'Buscar sugestões de séries' }}</button>
        <p v-if="searched && !suggestions.length">Nenhuma sugestão disponível.</p>
        <form v-for="suggestion in suggestions" :key="suggestion.hardcoverId" @submit.prevent="apply(suggestion)">
          <fieldset :disabled="busy">
            <legend>{{ suggestion.name }}</legend>
            <p>Selecione os livros do acervo. Novos vínculos serão adicionados ao final.</p>
            <label v-for="book in suggestion.books" :key="book.id" class="group-check"
              ><input v-model="selection[suggestion.hardcoverId]" type="checkbox" :value="book.id" />{{
                book.name
              }}</label
            >
            <label
              >Série de destino<select v-model="targets[suggestion.hardcoverId]">
                <option value="">Criar nova série</option>
                <option v-for="series in allSeries" :key="series.id" :value="series.id">{{ series.name }}</option>
              </select></label
            >
            <label v-if="!targets[suggestion.hardcoverId]"
              >Nome da nova série<input v-model="names[suggestion.hardcoverId]" required maxlength="300"
            /></label>
            <button type="submit" :disabled="!selection[suggestion.hardcoverId]?.length">Aplicar seleção</button>
          </fieldset>
        </form>
        <NuxtLink to="/books/series">Todas as séries</NuxtLink>
      </div>
    </details>
    <p v-if="message" role="status">{{ message }}</p>
  </section>
</template>
<script setup lang="ts">
import type { BookGroup, BookGroupDetails, BookSeriesSuggestion } from '~/types/book-series'
import { bookGroupError, useBookGroupsApi } from '~/composables/useBookGroupsApi'
import '~/assets/css/book-groups.css'
const props = defineProps<{ bookId: number }>()
const api = useBookGroupsApi()
const {
  data: memberships,
  status,
  error,
  refresh,
} = await useAsyncData(`book-series-${props.bookId}`, () => api<BookGroup[]>(`/${props.bookId}/series`))
const allSeries = ref<BookGroup[]>([])
const selectedSeries = ref<number | ''>('')
const newName = ref('')
const suggestions = ref<BookSeriesSuggestion[]>([])
const selection = ref<Record<number, number[]>>({})
const targets = ref<Record<number, number | ''>>({})
const names = ref<Record<number, string>>({})
const searched = ref(false)
const busy = ref(false)
const message = ref('')
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
async function loadSeries(event: Event) {
  if ((event.target as HTMLDetailsElement).open)
    await run(async () => {
      allSeries.value = await api<BookGroup[]>('/series')
    })
}
async function associate() {
  await run(async () => {
    await api(`/series/${selectedSeries.value}/items/${props.bookId}`, 'POST')
    await refresh()
    message.value = 'Livro associado.'
  })
}
async function create() {
  await run(async () => {
    const series = await api<BookGroupDetails>('/series', 'POST', { name: newName.value })
    // Preserve the created series as the selected target if association needs retry.
    selectedSeries.value = series.id
    allSeries.value = await api<BookGroup[]>('/series')
    newName.value = ''
    await api(`/series/${series.id}/items/${props.bookId}`, 'POST')
    await refresh()
    message.value = 'Série criada e livro associado.'
  })
}
async function unlink(id: number) {
  await run(async () => {
    await api(`/series/${id}/items/${props.bookId}`, 'DELETE')
    await refresh()
    message.value = 'Vínculo removido.'
  })
}
async function preview() {
  await run(async () => {
    suggestions.value = await api<BookSeriesSuggestion[]>(`/${props.bookId}/series/suggestions`, 'POST')
    allSeries.value = await api<BookGroup[]>('/series')
    for (const suggestion of suggestions.value) {
      selection.value[suggestion.hardcoverId] = suggestion.books.map((book) => book.id)
      targets.value[suggestion.hardcoverId] = ''
      names.value[suggestion.hardcoverId] = suggestion.name
    }
    searched.value = true
  })
}
async function apply(suggestion: BookSeriesSuggestion) {
  await run(async () => {
    const result = await api<BookGroupDetails>(`/${props.bookId}/series/suggestions/apply`, 'POST', {
      hardcoverId: suggestion.hardcoverId,
      seriesId: targets.value[suggestion.hardcoverId] || null,
      name: names.value[suggestion.hardcoverId],
      bookIds: selection.value[suggestion.hardcoverId],
    })
    targets.value[suggestion.hardcoverId] = result.id
    allSeries.value = await api<BookGroup[]>('/series')
    await refresh()
    message.value = 'Seleção aplicada.'
  })
}
</script>
