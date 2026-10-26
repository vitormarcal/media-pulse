<template>
  <main class="art-page">
    <header class="art-header">
      <h1>Listas de obras</h1>
      <button v-owner class="art-button art-primary" @click="adding = !adding">Criar lista</button>
    </header>
    <ArtworkNav />
    <form v-if="adding" v-owner class="art-panel" @submit.prevent="save">
      <fieldset :disabled="busy">
        <div class="art-fields">
          <label>Nome <input v-model="form.name" required maxlength="200" /></label
          ><label>Descrição <textarea v-model="form.description" maxlength="20000" /></label>
        </div>
        <div class="art-actions">
          <button class="art-button art-primary">Salvar lista</button
          ><button v-owner type="button" class="art-button" @click="adding = false">Cancelar</button>
        </div>
      </fieldset>
    </form>
    <p v-if="error" class="art-error" role="alert">{{ error }}</p>
    <p v-if="pending" role="status">Carregando listas…</p>
    <p v-if="loadError" class="art-error" role="alert">
      Não foi possível carregar as listas. <button class="art-button" @click="refresh()">Tentar novamente</button>
    </p>
    <p v-if="data && !data.length" class="art-muted">Nenhuma lista criada.</p>
    <div class="art-directory">
      <NuxtLink v-for="list in data" :key="list.id" :to="`/artworks/lists/${list.id}`" class="art-panel"
        ><h2>{{ list.name }}</h2>
        <p class="art-muted">{{ list.count }} {{ list.count === 1 ? 'obra' : 'obras' }}</p>
        <p>{{ list.description }}</p></NuxtLink
      >
    </div>
  </main>
</template>
<script setup lang="ts">
import ArtworkNav from '~/components/artworks/ArtworkNav.vue'
import type { ArtworkList } from '~/types/artworks'
const { request, run, busy, error } = useArtworkApi()
const {
  data,
  pending,
  error: loadError,
  refresh,
} = await useAsyncData('artwork-list-directory', () => request<ArtworkList[]>('/lists'))
const adding = ref(false)
const form = reactive({ name: '', description: '' })
async function save() {
  await run(async () => {
    const list = await request<ArtworkList>('/lists', { method: 'POST', body: form })
    await navigateTo(`/artworks/lists/${list.id}`)
  })
}
</script>
<style src="../../../assets/css/artworks.css"></style>
