<template>
  <fieldset class="month-field">
    <legend>Mês/ano</legend>
    <div class="month-inputs">
      <label
        ><span class="sr-only">Mês</span
        ><select v-model="month" :required="required || Boolean(year)" @change="update">
          <option value="">Mês</option>
          <option v-for="(name, index) in months" :key="name" :value="String(index + 1).padStart(2, '0')">
            {{ name }}
          </option>
        </select></label
      >
      <label
        ><span class="sr-only">Ano</span
        ><input
          v-model="year"
          type="number"
          inputmode="numeric"
          min="1"
          max="9999"
          step="1"
          placeholder="Ano"
          :required="required || Boolean(month)"
          @input="update"
      /></label>
    </div>
  </fieldset>
</template>
<script setup lang="ts">
const props = defineProps<{ required?: boolean }>()
const model = defineModel<string>({ required: true })
const months = [
  'Janeiro',
  'Fevereiro',
  'Março',
  'Abril',
  'Maio',
  'Junho',
  'Julho',
  'Agosto',
  'Setembro',
  'Outubro',
  'Novembro',
  'Dezembro',
]
const month = ref(model.value.split('-')[1] || '')
const year = ref(model.value.split('-')[0] || '')
function formattedValue() {
  return month.value && Number(year.value) >= 1 && Number(year.value) <= 9999
    ? `${String(year.value).padStart(4, '0')}-${month.value}`
    : ''
}
function update() {
  model.value = formattedValue()
}
watch(model, (value) => {
  if (value === formattedValue()) return
  const [y, m] = value.split('-')
  year.value = y || ''
  month.value = m || ''
})
const required = computed(() => props.required)
</script>
<style scoped>
.month-field {
  border: 0;
  padding: 0;
  margin: 0;
  min-width: 0;
}
legend {
  padding: 0;
  margin-bottom: 8px;
}
.month-inputs {
  display: grid;
  grid-template-columns: minmax(0, 1fr) 7rem;
  gap: 12px;
}
select,
input {
  width: 100%;
  padding: 11px 15px;
  border: 1px solid var(--base-color-border);
  border-radius: 16px;
  background: white;
  color: var(--base-color-text-primary);
}
select:focus,
input:focus {
  outline: 3px solid color-mix(in srgb, var(--base-color-focus) 66%, white);
  outline-offset: 2px;
}
.sr-only {
  position: absolute;
  width: 1px;
  height: 1px;
  overflow: hidden;
  clip-path: inset(50%);
  white-space: nowrap;
}
</style>
