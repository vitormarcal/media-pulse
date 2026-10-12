<template>
  <main class="art-page">
    <NuxtLink to="/artworks">Voltar para artes visuais</NuxtLink>
    <p v-if="pending && !data" role="status">Carregando obra…</p>
    <p v-else-if="loadError && !data" class="art-error" role="alert">
      {{ loadError.statusCode === 404 ? 'Obra não encontrada.' : 'Não foi possível carregar a obra.' }}
      <button class="art-button" @click="refresh()">Tentar novamente</button>
    </p>
    <template v-if="data">
      <header class="art-header">
        <h1>{{ data.artwork.title }}</h1>
        <div class="art-actions">
          <button class="art-button" :aria-pressed="data.artwork.favorite" :disabled="busy" @click="favorite">
            {{ data.artwork.favorite ? '♥ Favorita' : 'Favoritar' }}
          </button>
          <button class="art-button" @click="editing = !editing">Editar obra</button>
          <button class="art-button" :disabled="busy" @click="remove">Excluir obra</button>
        </div>
      </header>
      <ArtworkNav />
      <p v-if="error" class="art-error" role="alert">{{ error }}</p>
      <ArtworkForm v-if="editing" :initial="data.artwork" @saved="updated" @cancel="editing = false" />
      <div class="art-detail-top">
        <img
          v-if="primary"
          class="art-full-image"
          :src="resolveMediaUrl(primary.url) || undefined"
          :alt="primary.caption || data.artwork.title"
        />
        <div v-else class="art-image-fallback">Sem imagem</div>
        <section class="art-panel">
          <dl class="art-metadata">
            <template v-if="data.artist"
              ><dt>Artista</dt>
              <dd>
                <NuxtLink :to="`/artworks/artists/${data.artist.id}`">{{ data.artist.name }}</NuxtLink>
              </dd></template
            >
            <template v-if="data.artwork.year != null"
              ><dt>Ano</dt>
              <dd>{{ data.artwork.year }}</dd></template
            >
            <template v-if="data.artwork.type"
              ><dt>Tipo</dt>
              <dd>{{ data.artwork.type }}</dd></template
            >
            <template v-if="data.artwork.movement"
              ><dt>Movimento</dt>
              <dd>{{ data.artwork.movement }}</dd></template
            >
            <template v-if="data.artwork.technique"
              ><dt>Técnica</dt>
              <dd>{{ data.artwork.technique }}</dd></template
            >
          </dl>
          <p v-if="data.artwork.description" class="art-preserve">{{ data.artwork.description }}</p>
          <MediaRatingPanel
            media-type="artworks"
            :entity-id="data.artwork.id"
            :initial-rating="data.rating"
            compact
            @changed="refresh()"
          />
        </section>
      </div>
      <ArtworkImageManager
        :artwork-id="data.artwork.id"
        :images="data.images"
        :title="data.artwork.title"
        @changed="refresh()"
      />
      <ArtworkListMembership :artwork-id="data.artwork.id" :selected="data.lists" @changed="refresh()" />
      <ArtworkExperienceHistory :artwork-id="data.artwork.id" :experiences="data.experiences" @changed="refresh()" />
    </template>
  </main>
</template>
<script setup lang="ts">
import ArtworkNav from '~/components/artworks/ArtworkNav.vue'
import ArtworkForm from '~/components/artworks/ArtworkForm.vue'
import ArtworkImageManager from '~/components/artworks/ArtworkImageManager.vue'
import ArtworkListMembership from '~/components/artworks/ArtworkListMembership.vue'
import ArtworkExperienceHistory from '~/components/artworks/ArtworkExperienceHistory.vue'
import MediaRatingPanel from '~/components/media/MediaRatingPanel.vue'
import type { ArtworkDetails } from '~/types/artworks'
definePageMeta({ key: (route) => route.params.id as string })
const route = useRoute()
const { request, run, busy, error } = useArtworkApi()
const { resolveMediaUrl } = useMediaUrl()
const {
  data,
  pending,
  error: loadError,
  refresh,
} = await useAsyncData(`artwork-${route.params.id}`, () => request<ArtworkDetails>(`/${route.params.id}`))
const primary = computed(() => data.value?.images.find((image) => image.primary) || data.value?.images[0])
const editing = ref(false)
async function updated() {
  editing.value = false
  await refresh()
}
async function favorite() {
  await run(async () => {
    data.value = await request<ArtworkDetails>(`/${route.params.id}/favorite`, {
      method: 'PUT',
      body: { favorite: !data.value?.artwork.favorite },
    })
  })
}
async function remove() {
  if (!window.confirm('Excluir a obra e suas experiências e imagens? Artistas e listas serão preservados.')) return
  await run(async () => {
    await request(`/${route.params.id}`, { method: 'DELETE' })
    await navigateTo('/artworks')
  })
}
</script>
<style src="../../assets/css/artworks.css"></style>
