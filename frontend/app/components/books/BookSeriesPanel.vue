<template>
  <section class="book-groups group-panel" aria-labelledby="book-series-heading">
    <div class="group-section-heading">
      <h2 id="book-series-heading">Séries</h2>
      <button
        v-owner
        :aria-expanded="editing"
        aria-controls="book-series-management"
        :disabled="busy"
        @click="toggleEditing"
      >
        {{ editing ? 'Fechar gerenciamento' : 'Gerenciar séries' }}
      </button>
    </div>
    <p v-if="status === 'pending'" class="group-state">Carregando séries...</p>
    <p v-else-if="error" class="group-state group-error" role="alert">
      Não foi possível carregar as séries. <button @click="refresh()">Tentar novamente</button>
    </p>
    <div v-else-if="memberships?.length" class="group-grid">
      <BookGroupCard v-for="series in memberships" :key="series.id" :group="series" />
    </div>
    <p v-else class="group-muted">Nenhuma série associada.</p>
    <div v-if="editing" id="book-series-management" class="group-section">
      <div class="group-edit-panel">
        <form v-owner @submit.prevent="associate">
          <h3>Adicionar a uma série existente</h3>
          <label for="book-existing-series">Série local</label
          ><select id="book-existing-series" ref="seriesSelect" v-model="selectedSeries" required :disabled="busy">
            <option value="">Selecionar</option>
            <option v-for="series in availableSeries" :key="series.id" :value="series.id">
              {{ series.name }}
            </option></select
          ><button class="group-primary" type="submit" :disabled="busy || !selectedSeries">Associar livro</button>
        </form>
        <form v-owner @submit.prevent="create">
          <h3>Criar série</h3>
          <label>Nome<input v-model="newName" required maxlength="300" :disabled="busy" /></label
          ><button class="group-primary" type="submit" :disabled="busy || !newName.trim()">
            Criar série e associar livro
          </button>
        </form>
      </div>
      <div v-if="memberships?.length" class="group-actions">
        <button
          v-for="series in memberships"
          :key="series.id"
          v-owner
          class="group-danger"
          :disabled="busy"
          @click="unlink(series.id)"
        >
          Remover vínculo com {{ series.name }}
        </button>
      </div>
      <section class="group-section group-enrichment">
        <div class="group-section-heading">
          <h3>Sugestões do Hardcover</h3>
          <button v-owner :disabled="busy" @click="preview">
            {{ searching ? 'Buscando...' : 'Buscar sugestões de séries' }}
          </button>
        </div>
        <p v-if="searched && !suggestions.length" class="group-muted">Nenhuma sugestão disponível.</p>
        <BookSeriesSuggestionCard
          v-for="suggestion in suggestions"
          :key="suggestion.hardcoverId"
          :suggestion="suggestion"
          :series="allSeries"
          :book-id="bookId"
          @applied="reload"
        />
      </section>
    </div>
    <p v-if="message" role="status" :class="{ 'group-error': failed }">{{ message }}</p>
    <NuxtLink to="/books/series">Todas as séries</NuxtLink>
  </section>
</template>
<script setup lang="ts">
import type { BookGroup, BookGroupDetails, BookSeriesSuggestion } from '~/types/book-series'
import { bookGroupError, useBookGroupsApi } from '~/composables/useBookGroupsApi'
import BookGroupCard from '~/components/books/BookGroupCard.vue'
import BookSeriesSuggestionCard from '~/components/books/BookSeriesSuggestionCard.vue'
import '~/assets/css/book-groups.css'
const props = defineProps<{ bookId: number }>()
const emit = defineEmits<{ changed: [] }>()
const api = useBookGroupsApi()
const {
  data: memberships,
  status,
  error,
  refresh,
} = await useAsyncData(`book-series-${props.bookId}`, () => api<BookGroup[]>(`/${props.bookId}/series`))
watch(memberships, () => emit('changed'))
const editing = ref(false)
const seriesSelect = ref<HTMLSelectElement | null>(null)
const allSeries = ref<BookGroup[]>([])
const availableSeries = computed(() =>
  allSeries.value.filter((series) => !memberships.value?.some((member) => member.id === series.id)),
)
const selectedSeries = ref<number | ''>('')
const newName = ref('')
const suggestions = ref<BookSeriesSuggestion[]>([])
const searched = ref(false)
const searching = ref(false)
const busy = ref(false)
const failed = ref(false)
const message = ref('')
async function run(action: () => Promise<void>) {
  if (busy.value) return
  busy.value = true
  failed.value = false
  message.value = ''
  try {
    await action()
  } catch (cause) {
    failed.value = true
    message.value = bookGroupError(cause)
  } finally {
    busy.value = false
  }
}
async function reload() {
  await run(async () => {
    allSeries.value = await api<BookGroup[]>('/series')
    await refresh()
  })
}
async function toggleEditing() {
  editing.value = !editing.value
  if (editing.value) {
    await reload()
    await nextTick()
    seriesSelect.value?.focus()
  }
}
async function associate() {
  await run(async () => {
    await api(`/series/${selectedSeries.value}/items/${props.bookId}`, 'POST')
    await refresh()
    selectedSeries.value = ''
    message.value = 'Livro associado.'
  })
}
async function create() {
  await run(async () => {
    const series = await api<BookGroupDetails>('/series', 'POST', { name: newName.value })
    selectedSeries.value = series.id
    newName.value = ''
    allSeries.value = await api<BookGroup[]>('/series')
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
    searching.value = true
    try {
      const result = await api<BookSeriesSuggestion[]>(`/${props.bookId}/series/suggestions`, 'POST')
      allSeries.value = await api<BookGroup[]>('/series')
      suggestions.value = result
      searched.value = true
    } finally {
      searching.value = false
    }
  })
}
</script>
