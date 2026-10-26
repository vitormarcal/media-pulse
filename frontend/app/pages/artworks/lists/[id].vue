<template>
  <main class="art-page">
    <NuxtLink to="/artworks/lists">Voltar para listas</NuxtLink>
    <p v-if="pending && !data" role="status">Carregando lista…</p>
    <p v-if="loadError && !data" class="art-error" role="alert">
      {{ loadError.statusCode === 404 ? 'Lista não encontrada.' : 'Não foi possível carregar a lista.' }}
      <button class="art-button" @click="refresh()">Tentar novamente</button>
    </p>
    <template v-if="data">
      <header class="art-header">
        <h1>{{ data.name }}</h1>
        <div class="art-actions">
          <button v-owner class="art-button" @click="edit">Editar lista</button
          ><button v-owner class="art-button" :disabled="busy" @click="remove">Excluir lista</button>
        </div>
      </header>
      <ArtworkNav />
      <p v-if="data.description" class="art-preserve">{{ data.description }}</p>
      <form v-if="editing" v-owner class="art-panel" @submit.prevent="save">
        <fieldset :disabled="busy">
          <div class="art-fields">
            <label>Nome <input v-model="form.name" required maxlength="200" /></label
            ><label>Descrição <textarea v-model="form.description" maxlength="20000" /></label>
          </div>
          <div class="art-actions">
            <button class="art-button art-primary">Salvar lista</button
            ><button v-owner type="button" class="art-button" @click="editing = false">Cancelar</button>
          </div>
        </fieldset>
      </form>
      <p v-if="error" class="art-error" role="alert">{{ error }}</p>
      <ArtworkLibrary :list-id="data.id" />
    </template>
  </main>
</template>
<script setup lang="ts">
import ArtworkNav from '~/components/artworks/ArtworkNav.vue'
import ArtworkLibrary from '~/components/artworks/ArtworkLibrary.vue'
import type { ArtworkList } from '~/types/artworks'
definePageMeta({ key: (route) => route.params.id as string })
const route = useRoute()
const { request, run, busy, error } = useArtworkApi()
const {
  data,
  pending,
  error: loadError,
  refresh,
} = await useAsyncData(`artwork-list-${route.params.id}`, () => request<ArtworkList>(`/lists/${route.params.id}`))
const editing = ref(false)
const form = reactive({ name: '', description: '' })
function edit() {
  if (!data.value) return
  Object.assign(form, { name: data.value.name, description: data.value.description || '' })
  editing.value = true
}
async function save() {
  await run(async () => {
    await request(`/lists/${route.params.id}`, { method: 'PUT', body: form })
    editing.value = false
    await refresh()
  })
}
async function remove() {
  if (!window.confirm('Excluir esta lista? As obras serão preservadas.')) return
  await run(async () => {
    await request(`/lists/${route.params.id}`, { method: 'DELETE' })
    await navigateTo('/artworks/lists')
  })
}
</script>
<style src="../../../assets/css/artworks.css"></style>
