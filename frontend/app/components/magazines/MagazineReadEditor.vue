<template>
  <form class="mag-panel mag-form" @submit.prevent="save">
    <h2>{{ existing ? 'Atualizar leitura' : reread ? 'Registrar releitura' : 'Registrar leitura' }}</h2>
    <fieldset :disabled="saving" class="mag-form">
      <MagazineReadFields
        v-model="draft"
        :total-pages="issue.totalPages"
        :preserve-start="Boolean(existing?.startedAt)"
      />
      <div class="mag-actions">
        <button type="submit" class="mag-button primary">{{ saving ? 'Salvando…' : 'Salvar leitura' }}</button
        ><button type="button" class="mag-button" @click="$emit('cancel')">Fechar</button>
      </div>
    </fieldset>
    <p v-if="error" class="mag-error" role="alert">{{ error }}</p>
  </form>
</template>
<script setup lang="ts">
import type { MagazineDetails, MagazineIssue, MagazineRead, MagazineReadRequest } from '~/types/magazines'
import { localToday, magazineError, readPayload } from '~/utils/magazines'
import MagazineReadFields from './MagazineReadFields.vue'
const props = defineProps<{ issue: MagazineIssue; existing?: MagazineRead; reread?: boolean }>()
const emit = defineEmits<{ saved: [MagazineDetails]; cancel: [] }>()
const config = useRuntimeConfig()
const draft = ref<MagazineReadRequest>(
  props.existing
    ? {
        status: props.existing.status,
        startedAt: props.existing.startedAt,
        finishedAt: props.existing.finishedAt,
        progressPct: props.existing.currentPage == null ? props.existing.progressPct : null,
        currentPage: props.existing.currentPage,
      }
    : { status: 'CURRENTLY_READING', startedAt: localToday(), progressPct: 0 },
)
const saving = ref(false)
const error = ref('')
async function save() {
  saving.value = true
  error.value = ''
  try {
    const result = await $fetch<MagazineDetails>(
      `/api/magazines/${props.issue.id}/reads${props.existing ? `/${props.existing.id}` : ''}`,
      { baseURL: config.public.apiBase, method: props.existing ? 'PUT' : 'POST', body: readPayload(draft.value) },
    )
    emit('saved', result)
  } catch (cause) {
    error.value = magazineError(cause)
  } finally {
    saving.value = false
  }
}
</script>
<style scoped src="../../assets/css/magazines.css"></style>
