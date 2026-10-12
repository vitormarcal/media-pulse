<template>
  <main class="art-page">
    <NuxtLink to="/artworks/artists">Voltar para artistas</NuxtLink>
    <p v-if="pending && !data" role="status">Carregando artista…</p>
    <p v-if="loadError && !data" class="art-error" role="alert">
      {{ loadError.statusCode === 404 ? 'Artista não encontrado.' : 'Não foi possível carregar o artista.' }}
      <button class="art-button" @click="refresh()">Tentar novamente</button>
    </p>
    <template v-if="data">
      <header class="art-header">
        <h1>{{ data.name }}</h1>
        <div class="art-actions">
          <button class="art-button" @click="edit">Editar artista</button
          ><button class="art-button" :disabled="busy" @click="remove">Excluir artista</button>
        </div>
      </header>
      <ArtworkNav />
      <div class="art-detail-top">
        <img
          v-if="data.portraitUrl"
          class="art-portrait"
          :src="resolveMediaUrl(data.portraitUrl) || undefined"
          :alt="data.name"
        />
        <section>
          <p v-if="data.nationality">{{ data.nationality }}</p>
          <p v-if="data.bornAt">Nascimento: {{ formatDate(data.bornAt) }}</p>
          <p v-if="data.diedAt">Falecimento: {{ formatDate(data.diedAt) }}</p>
          <p v-if="data.biography" class="art-preserve">{{ data.biography }}</p>
        </section>
      </div>
      <form v-if="editing" class="art-panel" @submit.prevent="save">
        <fieldset :disabled="busy">
          <ArtworkArtistFields v-model="form" />
          <div class="art-actions">
            <button class="art-button art-primary">Salvar artista</button
            ><button type="button" class="art-button" @click="editing = false">Cancelar</button>
          </div>
        </fieldset>
      </form>
      <p v-if="error" class="art-error" role="alert">{{ error }}</p>
      <ArtworkImageManager :artist-id="data.id" @changed="refresh()" />
      <h2>Obras cadastradas</h2>
      <ArtworkLibrary :artist-id="data.id" />
    </template>
  </main>
</template>
<script setup lang="ts">
import ArtworkNav from '~/components/artworks/ArtworkNav.vue'
import ArtworkArtistFields from '~/components/artworks/ArtworkArtistFields.vue'
import ArtworkImageManager from '~/components/artworks/ArtworkImageManager.vue'
import ArtworkLibrary from '~/components/artworks/ArtworkLibrary.vue'
import type { ArtworkArtist } from '~/types/artworks'
definePageMeta({ key: (route) => route.params.id as string })
const route = useRoute()
const { request, run, busy, error } = useArtworkApi()
const { resolveMediaUrl } = useMediaUrl()
const {
  data,
  pending,
  error: loadError,
  refresh,
} = await useAsyncData(`artwork-artist-${route.params.id}`, () => request<ArtworkArtist>(`/artists/${route.params.id}`))
const editing = ref(false)
const form = ref({
  name: '',
  biography: null as string | null,
  nationality: null as string | null,
  bornAt: null as string | null,
  diedAt: null as string | null,
})
function formatDate(date: string) {
  return new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' }).format(new Date(`${date}T12:00:00Z`))
}
function edit() {
  if (!data.value) return
  form.value = {
    name: data.value.name,
    biography: data.value.biography,
    nationality: data.value.nationality,
    bornAt: data.value.bornAt,
    diedAt: data.value.diedAt,
  }
  editing.value = true
}
async function save() {
  await run(async () => {
    await request(`/artists/${route.params.id}`, {
      method: 'PUT',
      body: { ...form.value, bornAt: form.value.bornAt || null, diedAt: form.value.diedAt || null },
    })
    editing.value = false
    await refresh()
  })
}
async function remove() {
  if (!window.confirm('Excluir este artista e desvincular suas obras? As obras serão preservadas.')) return
  await run(async () => {
    await request(`/artists/${route.params.id}`, { method: 'DELETE', query: { detach: true } })
    await navigateTo('/artworks/artists')
  })
}
</script>
<style src="../../../assets/css/artworks.css"></style>
