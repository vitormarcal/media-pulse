<template>
  <form class="mag-form" @submit.prevent="save">
    <h3>Editar publicação</h3>
    <p class="mag-muted">Altera todos os números desta publicação.</p>
    <fieldset class="mag-fields" :disabled="saving">
      <label>Nome<input v-model="name" required maxlength="200" /></label
      ><label>ISSN<input v-model="issn" maxlength="9" /></label>
    </fieldset>
    <p v-if="error" role="alert" class="mag-error">{{ error }}</p>
    <div class="mag-actions">
      <button class="mag-button primary" :disabled="saving">{{ saving ? 'Salvando…' : 'Salvar publicação' }}</button
      ><button class="mag-button" type="button" :disabled="saving" @click="$emit('cancel')">Cancelar</button>
    </div>
  </form>
</template>
<script setup lang="ts">
import type { MagazinePublication } from '~/types/magazines'
import { magazineError } from '~/utils/magazines'
const props = defineProps<{ publication: MagazinePublication }>()
const emit = defineEmits<{ saved: []; cancel: [] }>()
const config = useRuntimeConfig()
const name = ref(props.publication.name),
  issn = ref(props.publication.issn || ''),
  saving = ref(false),
  error = ref('')
async function save() {
  if (saving.value) return
  saving.value = true
  error.value = ''
  try {
    await $fetch(`/api/magazines/publications/${props.publication.id}`, {
      baseURL: config.public.apiBase,
      method: 'PUT',
      body: { name: name.value, issn: issn.value || null },
    })
    emit('saved')
  } catch (cause) {
    error.value = magazineError(cause)
  } finally {
    saving.value = false
  }
}
</script>
<style scoped src="../../assets/css/magazines.css"></style>
