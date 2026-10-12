<template>
  <main class="art-page">
    <NuxtLink to="/artworks/artists">Voltar para artistas</NuxtLink>
    <p v-if="pending && !data" role="status">Carregando artista…</p>
    <p v-if="loadError && !data" class="art-error" role="alert">
      {{ loadError.statusCode === 404 ? 'Artista não encontrado.' : 'Não foi possível carregar o artista.' }}
      <button class="art-button" @click="refresh()">Tentar novamente</button>
    </p>
    <template v-if="data">
      <ArtworkNav />
      <header class="art-artist-heading" :class="{ 'has-portrait': Boolean(data.portraitUrl) }">
        <img
          v-if="data.portraitUrl"
          class="art-artist-portrait"
          :src="resolveMediaUrl(data.portraitUrl) || undefined"
          :alt="data.name"
        />
        <div>
          <h1>{{ data.name }}</h1>
          <p v-if="data.countryOfOrigin" class="art-muted">{{ data.countryOfOrigin }}</p>
          <dl v-if="data.bornAt || data.diedAt" class="art-life-dates">
            <template v-if="data.bornAt"
              ><dt>Nascimento</dt>
              <dd>{{ formatDate(data.bornAt) }}</dd></template
            >
            <template v-if="data.diedAt"
              ><dt>Falecimento</dt>
              <dd>{{ formatDate(data.diedAt) }}</dd></template
            >
          </dl>
          <div class="art-actions">
            <button class="art-button" :aria-expanded="editing" @click="edit">
              {{ editing ? 'Fechar edição' : 'Editar artista' }}
            </button>
            <button v-if="editing" class="art-button" :disabled="busy" @click="remove">Excluir artista</button>
          </div>
          <ArtworkLongText title="Biografia" :text="data.biography" />
        </div>
      </header>
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
      <ArtworkImageManager :artist-id="data.id" :has-portrait="Boolean(data.portraitUrl)" @changed="refresh()" />
      <h2>Obras cadastradas</h2>
      <ArtworkLibrary :artist-id="data.id" chronological :born-at="data.bornAt" :died-at="data.diedAt" />
    </template>
  </main>
</template>
<script setup lang="ts">
import ArtworkNav from '~/components/artworks/ArtworkNav.vue'
import ArtworkLongText from '~/components/artworks/ArtworkLongText.vue'
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
  countryOfOrigin: null as string | null,
  bornAt: null as string | null,
  diedAt: null as string | null,
})
function formatDate(date: string) {
  return new Intl.DateTimeFormat('pt-BR', { timeZone: 'UTC' }).format(new Date(`${date}T12:00:00Z`))
}
function edit() {
  if (editing.value) {
    editing.value = false
    return
  }
  if (!data.value) return
  form.value = {
    name: data.value.name,
    biography: data.value.biography,
    countryOfOrigin: data.value.countryOfOrigin,
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
