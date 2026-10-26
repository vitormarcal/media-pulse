<template>
  <div class="art-wide art-movements-field">
    <label :for="inputId">Movimentos e estilos</label>
    <div class="art-movement-input">
      <input
        :id="inputId"
        ref="input"
        v-model="draft"
        :list="suggestionId"
        maxlength="200"
        placeholder="Ex.: Art Nouveau"
        @keydown.enter.prevent="add"
      />
      <button v-owner type="button" class="art-button" :disabled="!draft.trim()" @click="add">Adicionar</button>
    </div>
    <datalist :id="suggestionId">
      <option v-for="suggestion in available" :key="suggestion" :value="suggestion" />
    </datalist>
    <ul v-if="model.length" class="art-movement-chips" aria-label="Movimentos selecionados">
      <li v-for="(movement, index) in model" :key="movement">
        <span>{{ movement }}</span
        ><button v-owner type="button" :aria-label="`Remover ${movement}`" @click="remove(index)">×</button>
      </li>
    </ul>
  </div>
</template>
<script setup lang="ts">
const model = defineModel<string[]>({ required: true })
const props = defineProps<{ suggestions: string[] }>()
const inputId = useId()
const suggestionId = useId()
const input = ref<HTMLInputElement>()
const draft = ref('')
const normalized = (value: string) => value.trim().replace(/\s+/g, ' ').toLowerCase()
const available = computed(() =>
  props.suggestions.filter((value) => !model.value.some((term) => normalized(term) === normalized(value))),
)
function add() {
  const value = draft.value.trim().replace(/\s+/g, ' ')
  if (!value) return
  if (!model.value.some((term) => normalized(term) === normalized(value))) {
    const existing = props.suggestions.find((term) => normalized(term) === normalized(value))
    model.value = [...model.value, existing || value]
  }
  draft.value = ''
  input.value?.focus()
}
defineExpose({ commit: add })
function remove(index: number) {
  model.value = model.value.filter((_, position) => position !== index)
  input.value?.focus()
}
</script>
