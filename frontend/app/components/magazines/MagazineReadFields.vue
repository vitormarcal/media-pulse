<template>
  <fieldset class="mag-form">
    <legend>Leitura</legend>
    <label class="mag-field">
      <span>Estado</span>
      <select v-model="read.status" @change="changeStatus">
        <option v-for="(label, value) in magazineStates" :key="value" :value="value">{{ label }}</option>
      </select>
    </label>
    <div v-if="read.status !== 'WANT_TO_READ'" class="mag-fields">
      <label
        ><span>{{ read.status === 'READ' ? 'Início (opcional ao marcar diretamente)' : 'Data de início' }}</span
        ><input v-model="read.startedAt" type="date" :required="read.status !== 'READ'"
      /></label>
      <label v-if="read.status === 'READ'"
        ><span>Data de término</span
        ><input v-model="read.finishedAt" type="date" :min="read.startedAt || undefined" required
      /></label>
      <template v-else>
        <label
          ><span>Informar progresso por</span
          ><select v-model="mode" @change="changeMode">
            <option value="percent">Porcentagem</option>
            <option v-if="totalPages" value="page">Página atual</option>
          </select></label
        >
        <label v-if="mode === 'page'"
          ><span>Página atual de {{ totalPages }}</span
          ><input
            v-model.number="read.currentPage"
            type="number"
            min="0"
            :max="totalPages || undefined"
            step="1"
            required
          /><small>{{ calculatedProgress }}% lida</small></label
        >
        <label v-else
          ><span>Progresso (%)</span
          ><input v-model.number="read.progressPct" type="number" min="0" max="100" step="any" required
        /></label>
      </template>
    </div>
  </fieldset>
</template>
<script setup lang="ts">
import type { MagazineReadRequest } from '~/types/magazines'
import { localToday, magazineStates } from '~/utils/magazines'
const props = defineProps<{ totalPages?: number | null; preserveStart?: boolean }>()
const read = defineModel<MagazineReadRequest>({ required: true })
const mode = ref(read.value.currentPage != null && props.totalPages ? 'page' : 'percent')
const calculatedProgress = computed(() =>
  props.totalPages ? Math.round(((Number(read.value.currentPage) || 0) / props.totalPages) * 100) : 0,
)
function changeMode() {
  if (mode.value === 'percent') {
    read.value.progressPct = calculatedProgress.value
    read.value.currentPage = null
  } else {
    read.value.currentPage = Math.round(((read.value.progressPct || 0) / 100) * (props.totalPages || 0))
    read.value.progressPct = null
  }
}
function changeStatus() {
  if (read.value.status === 'WANT_TO_READ') {
    read.value.startedAt = null
    read.value.finishedAt = null
    read.value.progressPct = 0
    read.value.currentPage = null
    mode.value = 'percent'
  } else if (read.value.status === 'READ') {
    if (!props.preserveStart) read.value.startedAt = null
    read.value.finishedAt ||= localToday()
  } else {
    read.value.startedAt ||= localToday()
    read.value.finishedAt = null
  }
}
watch(
  () => props.totalPages,
  (total) => {
    if (!total && mode.value === 'page') {
      mode.value = 'percent'
      read.value.currentPage = null
      read.value.progressPct ||= 0
    }
  },
)
</script>
<style scoped src="../../assets/css/magazines.css"></style>
