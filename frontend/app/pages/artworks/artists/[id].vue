<template>
  <main class="art-page art-detail-review art-artist-review">
    <NuxtLink to="/artworks/artists">Voltar para artistas</NuxtLink>
    <p v-if="pending && !data" role="status">Carregando artista…</p>
    <p v-if="loadError && !data" class="art-error" role="alert">
      {{ loadError.statusCode === 404 ? 'Artista não encontrado.' : 'Não foi possível carregar o artista.' }}
      <button class="art-button" @click="refresh()">Tentar novamente</button>
    </p>
    <template v-if="data">
      <ArtworkNav />
      <header class="art-artist-heading" :class="{ 'has-portrait': Boolean(portrait) }">
        <div class="art-artist-identity">
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
        </div>
        <figure v-if="portrait" class="art-artist-picture">
          <button type="button" :aria-label="`Ampliar imagem de ${data.name}`" @click="portraitGallery?.open(0)">
            <img
              :src="resolveMediaUrl(portrait) || undefined"
              :alt="data.name"
              @error="failedPortrait = portrait"
            /></button
          ><button type="button" class="art-button" @click="portraitGallery?.open(0)">Ver imagem</button>
        </figure>
        <ArtworkLongText :preview-length="420" title="Biografia" :text="data.biography" />
        <MediaImageGallery v-if="portrait" ref="portraitGallery" :title="data.name" :images="[portrait]" viewer-only />
      </header>
      <section class="art-local-works">
        <h2>Obras no acervo</h2>
        <ArtworkLibrary :artist-id="data.id" chronological compact-chronology />
      </section>
      <section v-if="owner.authenticated" class="art-panel art-artist-maintenance">
        <div class="art-section-header">
          <h2>Sobre o artista</h2>
          <div class="art-actions">
            <button v-owner class="art-button" :aria-expanded="editing" @click="edit">
              {{ editing ? 'Fechar edição' : 'Editar artista' }}
            </button>
            <button v-if="editing" v-owner class="art-button" :disabled="busy" @click="remove">Excluir artista</button>
          </div>
        </div>
        <form v-if="editing" v-owner class="art-panel" @submit.prevent="save">
          <fieldset :disabled="busy">
            <ArtworkArtistFields v-model="form" />
            <div class="art-actions">
              <button class="art-button art-primary">Salvar artista</button
              ><button v-owner type="button" class="art-button" @click="editing = false">Cancelar</button>
            </div>
          </fieldset>
        </form>
        <p v-if="error" class="art-error" role="alert">{{ error }}</p>
        <ArtworkImageManager
          v-if="owner.authenticated"
          :artist-id="data.id"
          :has-portrait="Boolean(data.portraitUrl)"
          @changed="refresh()"
        />
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import MediaImageGallery from '~/components/media/MediaImageGallery.vue'
import ArtworkNav from '~/components/artworks/ArtworkNav.vue'
import ArtworkLongText from '~/components/artworks/ArtworkLongText.vue'
import ArtworkArtistFields from '~/components/artworks/ArtworkArtistFields.vue'
import ArtworkImageManager from '~/components/artworks/ArtworkImageManager.vue'
import ArtworkLibrary from '~/components/artworks/ArtworkLibrary.vue'
import type { ArtworkArtist } from '~/types/artworks'
definePageMeta({ key: (route) => route.params.id as string })
const owner = useOwnerAuth()
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
const editingInitialized = ref(false)
const failedPortrait = ref<string | null>(null)
const portrait = computed(() =>
  data.value?.portraitUrl && data.value.portraitUrl !== failedPortrait.value ? data.value.portraitUrl : null,
)
const portraitGallery = ref<InstanceType<typeof MediaImageGallery> | null>(null)
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
  if (!editingInitialized.value)
    form.value = {
      name: data.value.name,
      biography: data.value.biography,
      countryOfOrigin: data.value.countryOfOrigin,
      bornAt: data.value.bornAt,
      diedAt: data.value.diedAt,
    }
  editingInitialized.value = true
  editing.value = true
}
async function save() {
  await run(async () => {
    await request(`/artists/${route.params.id}`, {
      method: 'PUT',
      body: { ...form.value, bornAt: form.value.bornAt || null, diedAt: form.value.diedAt || null },
    })
    editing.value = false
    editingInitialized.value = false
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
