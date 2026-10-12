<template>
  <main class="art-page">
    <header class="art-header">
      <h1>Artes visuais</h1>
      <button class="art-button art-primary" @click="adding = !adding">Adicionar obra</button>
    </header>
    <ArtworkNav />
    <ArtworkForm v-if="adding" @saved="saved" @cancel="adding = false" />
    <ArtworkLibrary />
  </main>
</template>
<script setup lang="ts">
import ArtworkNav from '~/components/artworks/ArtworkNav.vue'
import ArtworkForm from '~/components/artworks/ArtworkForm.vue'
import ArtworkLibrary from '~/components/artworks/ArtworkLibrary.vue'
import type { ArtworkDetails } from '~/types/artworks'
const adding = ref(false)
async function saved(details: ArtworkDetails) {
  await navigateTo(`/artworks/${details.artwork.id}`)
}
</script>
<style src="../../assets/css/artworks.css"></style>
