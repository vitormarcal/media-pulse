<template>
  <form v-owner class="group-suggestion" @submit.prevent="apply">
    <header>
      <p class="group-eyebrow">Sugestão do Hardcover</p>
      <h3>{{ suggestion.name }}</h3>
      <p v-if="suggestion.hardcover.primaryAuthor" class="group-muted">
        Autor principal no Hardcover: {{ suggestion.hardcover.primaryAuthor.name }}
      </p>
    </header>
    <fieldset :disabled="busy || loadingTarget">
      <legend>Organizar no Media Pulse</legend>
      <label :for="`suggestion-target-${suggestion.hardcoverId}`">Destino</label
      ><select :id="`suggestion-target-${suggestion.hardcoverId}`" v-model="target">
        <option value="">Criar nova série</option>
        <option v-for="destination in compatibleSeries" :key="destination.id" :value="destination.id">
          Adicionar a {{ destination.name }}
        </option>
      </select>
      <label v-if="!target">Nome da nova série<input v-model="name" required maxlength="300" /></label>
      <p v-if="current" class="group-muted">
        Série local: <NuxtLink :to="`/books/series/${current.id}`">{{ current.name }}</NuxtLink>
      </p>
      <div class="group-section-heading">
        <h4>Livros do seu acervo</h4>
        <span class="group-muted">{{ selected.length }} selecionados</span>
      </div>
      <div class="group-suggestion-books">
        <label v-for="book in suggestion.books" :key="book.id" class="group-book-choice"
          ><input v-if="isLinked(book.id)" type="checkbox" checked disabled /><input
            v-else
            v-model="selected"
            type="checkbox"
            :value="book.id"
          /><MediaImage :src="resolveMediaUrl(book.coverUrl)" :alt="book.name" :fallback="book.name.slice(0, 1)" /><span
            >{{ book.name }}<small v-if="isLinked(book.id)">Já associado à série local</small></span
          ></label
        >
      </div>
      <p v-if="!suggestion.books.length" class="group-muted">Nenhum livro desta sugestão está no acervo local.</p>
      <BookSeriesMetadataFields v-model="fields" :suggestion="suggestion" :current="current || undefined" />
      <button
        class="group-primary"
        type="submit"
        :disabled="loadingTarget || targetFailed || (!selected.length && !(current?.hardcoverId && fields.length))"
      >
        {{ busy ? 'Aplicando...' : 'Aplicar seleção' }}
      </button>
    </fieldset>
    <p v-if="message" role="status" :class="{ 'group-error': failed }">{{ message }}</p>
    <button v-if="targetFailed" v-owner type="button" :disabled="loadingTarget" @click="loadTarget">
      Tentar carregar destino novamente
    </button>
  </form>
</template>
<script setup lang="ts">
import type { BookGroup, BookGroupDetails, BookSeriesMetadataField, BookSeriesSuggestion } from '~/types/book-series'
import { bookGroupError, useBookGroupsApi } from '~/composables/useBookGroupsApi'
import { defaultBookSeriesFields } from '~/utils/bookSeries'
import BookSeriesMetadataFields from '~/components/books/BookSeriesMetadataFields.vue'
const props = defineProps<{ suggestion: BookSeriesSuggestion; series: BookGroup[]; bookId: number }>()
const emit = defineEmits<{ applied: [] }>()
const api = useBookGroupsApi()
const { resolveMediaUrl } = useMediaUrl()
const target = ref<number | ''>('')
const name = ref(props.suggestion.name)
const selected = ref<number[]>(props.suggestion.books.map((book) => book.id))
const fields = ref<BookSeriesMetadataField[]>(defaultBookSeriesFields(props.suggestion))
const current = ref<BookGroupDetails | null>(null)
const busy = ref(false)
const loadingTarget = ref(false)
const targetFailed = ref(false)
const failed = ref(false)
const message = ref('')
const compatibleSeries = computed(() =>
  props.series.filter((series) => !series.hardcoverId || series.hardcoverId === props.suggestion.hardcoverId),
)
function isLinked(bookId: number) {
  return !!current.value?.items.some((book) => book.id === bookId)
}
async function loadTarget() {
  targetFailed.value = false
  message.value = ''
  current.value = null
  if (!target.value) {
    selected.value = props.suggestion.books.map((book) => book.id)
    fields.value = defaultBookSeriesFields(props.suggestion)
    return
  }
  loadingTarget.value = true
  try {
    current.value = await api<BookGroupDetails>(`/series/${target.value}`)
    selected.value = props.suggestion.books.filter((book) => !isLinked(book.id)).map((book) => book.id)
    fields.value = []
  } catch (cause) {
    failed.value = true
    targetFailed.value = true
    message.value = bookGroupError(cause)
  } finally {
    loadingTarget.value = false
  }
}
watch(target, (value) => {
  if (current.value?.id !== value) void loadTarget()
})
onMounted(() => {
  const matches = props.series.filter((series) => series.hardcoverId === props.suggestion.hardcoverId)
  if (matches.length === 1) target.value = matches[0]!.id
})
async function apply() {
  if (busy.value || loadingTarget.value || targetFailed.value) return
  busy.value = true
  failed.value = false
  message.value = ''
  try {
    const result = selected.value.length
      ? await api<BookGroupDetails>(`/${props.bookId}/series/suggestions/apply`, 'POST', {
          hardcoverId: props.suggestion.hardcoverId,
          seriesId: target.value || null,
          name: name.value,
          bookIds: selected.value,
          metadataFields: fields.value,
        })
      : await api<BookGroupDetails>(`/series/${target.value}/metadata/apply`, 'POST', { fields: fields.value })
    current.value = result
    target.value = result.id
    selected.value = []
    fields.value = []
    message.value = 'Seleção aplicada.'
    emit('applied')
  } catch (cause) {
    failed.value = true
    message.value = bookGroupError(cause)
  } finally {
    busy.value = false
  }
}
</script>
