<template>
  <div class="art-fields">
    <label>Nome <input v-model="value.name" required maxlength="200" /></label>
    <label>País de origem <input v-model="value.countryOfOrigin" maxlength="200" placeholder="França" /></label>
    <details class="art-wide art-form-details">
      <summary>Mais dados do artista</summary>
      <div class="art-fields">
        <label>Nascimento <input v-model="value.bornAt" type="date" /></label>
        <label>Falecimento <input v-model="value.diedAt" type="date" /></label>
        <label class="art-wide">Biografia <textarea v-model="value.biography" rows="4" maxlength="20000" /></label>
      </div>
    </details>
  </div>
</template>
<script setup lang="ts">
const value = defineModel<{
  name: string
  countryOfOrigin: string | null
  bornAt: string | null
  diedAt: string | null
  biography: string | null
}>({ required: true })
</script>
