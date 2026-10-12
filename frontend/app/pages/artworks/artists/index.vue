<template>
  <main class="art-page">
    <header class="art-header">
      <h1>Artistas</h1>
      <button class="art-button art-primary" @click="adding = !adding">Adicionar artista</button>
    </header>
    <ArtworkNav />
    <form v-if="adding" class="art-panel" @submit.prevent="save">
      <fieldset :disabled="busy">
        <ArtworkArtistFields v-model="form" />
        <div class="art-actions">
          <button class="art-button art-primary">Salvar artista</button
          ><button type="button" class="art-button" @click="adding = false">Cancelar</button>
        </div>
      </fieldset>
    </form>
    <p v-if="error" class="art-error" role="alert">{{ error }}</p>
    <p v-if="pending" role="status">Carregando artistas…</p>
    <p v-if="loadError" class="art-error" role="alert">
      Não foi possível carregar artistas. <button class="art-button" @click="refresh()">Tentar novamente</button>
    </p>
    <p v-if="data && !data.length" class="art-muted">Nenhum artista cadastrado.</p>
    <div class="art-directory">
      <NuxtLink v-for="artist in data" :key="artist.id" :to="`/artworks/artists/${artist.id}`" class="art-panel">
        <MediaImage
          v-if="artist.portraitUrl"
          class="art-portrait"
          :src="resolveMediaUrl(artist.portraitUrl) || undefined"
          :alt="artist.name"
          loading="lazy"
        />
        <h2>{{ artist.name }}</h2>
        <p class="art-muted">{{ artist.countryOfOrigin }}</p>
      </NuxtLink>
    </div>
  </main>
</template>
<script setup lang="ts">
import ArtworkNav from '~/components/artworks/ArtworkNav.vue'
import ArtworkArtistFields from '~/components/artworks/ArtworkArtistFields.vue'
import type { ArtworkArtist } from '~/types/artworks'
const { request, run, busy, error } = useArtworkApi()
const { resolveMediaUrl } = useMediaUrl()
const {
  data,
  pending,
  error: loadError,
  refresh,
} = await useAsyncData('artwork-artists-directory', () => request<ArtworkArtist[]>('/artists'))
const adding = ref(false)
const form = ref({
  name: '',
  biography: null as string | null,
  countryOfOrigin: null as string | null,
  bornAt: null as string | null,
  diedAt: null as string | null,
})
async function save() {
  await run(async () => {
    const artist = await request<ArtworkArtist>('/artists', {
      method: 'POST',
      body: { ...form.value, bornAt: form.value.bornAt || null, diedAt: form.value.diedAt || null },
    })
    await navigateTo(`/artworks/artists/${artist.id}`)
  })
}
</script>
<style src="../../../assets/css/artworks.css"></style>
