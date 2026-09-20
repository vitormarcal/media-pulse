<template>
  <form class="rules-panel" @submit.prevent="save">
    <h2>Incluir automaticamente</h2>
    <p>Itens adicionados manualmente permanecem. Com as duas regras ativas, basta atender a uma delas.</p>
    <fieldset :disabled="saving || disabled">
      <label><input v-model="favorites" type="checkbox" /> Incluir favoritos</label>
      <label><input v-model="abandoned" type="checkbox" /> Incluir abandonados</label>
      <button type="submit" :disabled="!dirty">{{ saving ? 'Salvando…' : 'Salvar regras' }}</button>
    </fieldset>
    <p>Ao desmarcar uma obra, ela sai da seleção automática. Se também foi adicionada manualmente, permanece.</p>
    <p v-if="feedback" role="status">{{ feedback }}</p>
  </form>
</template>

<script setup lang="ts">
const props = defineProps<{
  domain: 'movies' | 'shows'
  listId: number
  includeFavorites: boolean
  includeAbandoned: boolean
  disabled?: boolean
}>()
const emit = defineEmits<{ saved: [] }>()
const config = useRuntimeConfig()
const favorites = ref(props.includeFavorites)
const abandoned = ref(props.includeAbandoned)
const saving = ref(false)
const feedback = ref('')
const dirty = computed(() => favorites.value !== props.includeFavorites || abandoned.value !== props.includeAbandoned)
watch(
  () => [props.includeFavorites, props.includeAbandoned],
  () => {
    favorites.value = props.includeFavorites
    abandoned.value = props.includeAbandoned
  },
)
async function save() {
  if (saving.value || props.disabled || !dirty.value) return
  saving.value = true
  feedback.value = ''
  try {
    await $fetch(`/api/${props.domain}/lists/${props.listId}/rules`, {
      baseURL: config.public.apiBase,
      method: 'PATCH',
      body: { includeFavorites: favorites.value, includeAbandoned: abandoned.value },
    })
    feedback.value = 'Regras salvas.'
    emit('saved')
  } catch {
    feedback.value = 'Não foi possível salvar as regras. Tente novamente.'
  } finally {
    saving.value = false
  }
}
</script>

<style scoped>
.rules-panel {
  display: grid;
  gap: 16px;
  padding: 24px;
  border: 1px solid var(--base-color-border);
  border-radius: 32px;
  background: var(--base-color-surface-wash);
}
h2,
p {
  margin: 0;
}
p {
  color: var(--base-color-text-secondary);
}
fieldset {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 16px;
  border: 0;
  padding: 0;
  margin: 0;
}
label {
  display: flex;
  align-items: center;
  gap: 8px;
}
input {
  accent-color: var(--base-color-brand-red);
}
button {
  padding: 8px 14px;
  border: 0;
  border-radius: 16px;
  background: var(--base-color-brand-red);
  color: white;
  font: inherit;
  cursor: pointer;
}
button:disabled {
  opacity: 0.55;
  cursor: default;
}
</style>
