<template>
  <fieldset class="group-metadata-fields" :disabled="disabled">
    <legend>Metadados do Hardcover</legend>
    <p class="group-muted">Selecione os campos para aplicar.</p>
    <label v-for="field in fields" :key="field.key" class="group-field-choice">
      <input v-model="selected" type="checkbox" :value="field.key" />
      <span
        ><strong>{{ field.label }}</strong
        ><span v-if="current" class="group-current">Atual: {{ fieldValue(current, field.key) }}</span
        ><span class="group-suggested"
          >{{ current ? 'Hardcover: ' : '' }}{{ fieldValue(suggestion, field.key) }}</span
        ></span
      >
    </label>
  </fieldset>
</template>
<script setup lang="ts">
import type { BookGroupDetails, BookSeriesMetadataField, BookSeriesSuggestion } from '~/types/book-series'
const props = withDefaults(
  defineProps<{
    suggestion: BookSeriesSuggestion
    current?: BookGroupDetails
    includeName?: boolean
    disabled?: boolean
  }>(),
  { current: undefined, includeName: false, disabled: false },
)
const selected = defineModel<BookSeriesMetadataField[]>({ required: true })
const fields = computed(() => {
  const list: { key: BookSeriesMetadataField; label: string }[] = [
    { key: 'DESCRIPTION', label: 'Descrição' },
    { key: 'PRIMARY_AUTHOR', label: 'Autor principal no Hardcover' },
    { key: 'PUBLICATION_STATUS', label: 'Situação da publicação' },
    { key: 'COUNTS', label: 'Contagens no Hardcover' },
    { key: 'EXTERNAL_SLUG', label: 'Identificação no Hardcover' },
  ]
  return props.includeName ? [{ key: 'NAME' as const, label: 'Nome' }, ...list] : list
})
function fieldValue(value: BookSeriesSuggestion | BookGroupDetails, field: BookSeriesMetadataField) {
  switch (field) {
    case 'NAME':
      return value.name
    case 'DESCRIPTION':
      return value.description || 'Não informada'
    case 'PRIMARY_AUTHOR':
      return value.hardcover.primaryAuthor?.name || 'Não informado'
    case 'PUBLICATION_STATUS':
      return value.hardcover.isCompleted == null
        ? 'Não informada'
        : value.hardcover.isCompleted
          ? 'Publicação concluída'
          : 'Publicação não concluída'
    case 'COUNTS':
      return `${value.hardcover.booksCount ?? '—'} livros · ${value.hardcover.primaryBooksCount ?? '—'} volumes principais`
    case 'EXTERNAL_SLUG':
      return value.hardcover.slug || 'Não informada'
  }
}
</script>
