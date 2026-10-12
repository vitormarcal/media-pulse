<template>
  <div class="art-fields">
    <label>Nome <input v-model="value.name" required maxlength="200" /></label>
    <label>Nacionalidade <input v-model="value.nationality" maxlength="200" /></label>
    <label>Nascimento <input v-model="value.bornAt" type="date" /></label>
    <label>Falecimento <input v-model="value.diedAt" type="date" /></label>
    <label class="art-wide">Biografia <textarea v-model="value.biography" rows="3" maxlength="20000" /></label>
  </div>
</template>
<script setup lang="ts">
const value = defineModel<{
  name: string
  nationality: string | null
  bornAt: string | null
  diedAt: string | null
  biography: string | null
}>({ required: true })
</script>
