<template>
  <div class="art-fields">
    <label>Data <input v-model="value.experiencedAt" type="date" /></label>
    <label
      >Contato
      <select v-model="value.experienceType" aria-label="Contato">
        <option :value="null">Não informado</option>
        <option value="DIGITAL_VIEWING">Digital</option>
        <option value="IN_PERSON">Presencial</option>
      </select>
    </label>
    <label
      >Origem / local <input v-model="value.sourceName" maxlength="300" placeholder="Site, museu, exposição, livro…"
    /></label>
    <label>Link da origem <input v-model="value.sourceUrl" type="url" maxlength="4000" /></label>
    <label class="art-wide">Impressões <textarea v-model="value.notes" rows="3" maxlength="20000" /></label>
  </div>
</template>
<script setup lang="ts">
import type { ArtworkExperienceInput } from '~/types/artworks'
const value = defineModel<ArtworkExperienceInput>({ required: true })
</script>
