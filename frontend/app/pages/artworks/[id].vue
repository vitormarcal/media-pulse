<template>
  <main class="art-page">
    <NuxtLink to="/artworks">Voltar para artes visuais</NuxtLink>
    <p v-if="pending && !data" role="status">Carregando obra…</p>
    <p v-else-if="loadError && !data" class="art-error" role="alert">
      {{ loadError.statusCode === 404 ? 'Obra não encontrada.' : 'Não foi possível carregar a obra.' }}
      <button class="art-button" @click="refresh()">Tentar novamente</button>
    </p>
    <template v-if="data">
      <ArtworkNav />
      <header class="art-work-heading">
        <h1>{{ data.artwork.title }}</h1>
        <NuxtLink v-if="data.artist" :to="`/artworks/artists/${data.artist.id}`" class="art-creator-link">
          <img v-if="data.artist.portraitUrl" :src="resolveMediaUrl(data.artist.portraitUrl) || undefined" alt="" />
          <span>{{ data.artist.name }}</span>
        </NuxtLink>
        <p v-if="data.artist?.countryOfOrigin || data.artwork.year != null" class="art-muted">
          {{
            [data.artist?.countryOfOrigin, data.artwork.year].filter((part) => part != null && part !== '').join(' · ')
          }}
        </p>
        <div class="art-actions">
          <button class="art-button" :aria-pressed="data.artwork.favorite" :disabled="busy" @click="favorite">
            {{ data.artwork.favorite ? '♥ Favorita' : 'Favoritar' }}
          </button>
          <button class="art-button" :aria-expanded="editing" @click="editing = !editing">
            {{ editing ? 'Fechar edição' : 'Editar obra' }}
          </button>
          <button v-if="editing" class="art-button" :disabled="busy" @click="remove">Excluir obra</button>
        </div>
      </header>
      <p v-if="error" class="art-error" role="alert">{{ error }}</p>
      <ArtworkForm v-if="editing" :initial="data.artwork" @saved="updated" @cancel="editing = false" />
      <ArtworkGallery :images="data.images" :title="data.artwork.title" />
      <div class="art-reading-layout" :class="{ 'has-description': Boolean(data.artwork.description) }">
        <section class="art-work-facts" aria-label="Dados da obra">
          <dl v-if="data.artwork.type || data.artwork.movement || data.artwork.technique" class="art-metadata">
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
          <MediaRatingPanel
            media-type="artworks"
            :entity-id="data.artwork.id"
            :initial-rating="data.rating"
            compact
            @changed="refresh()"
          />
        </section>
        <ArtworkLongText title="Descrição" :text="data.artwork.description" />
      </div>
      <ArtworkExperienceHistory :artwork-id="data.artwork.id" :experiences="data.experiences" @changed="refresh()" />
      <ArtworkListMembership :artwork-id="data.artwork.id" :selected="data.lists" @changed="refresh()" />
      <ArtworkImageManager
        :artwork-id="data.artwork.id"
        :images="data.images"
        :title="data.artwork.title"
        management-only
        @changed="refresh()"
      />
    </template>
  </main>
</template>
<script setup lang="ts">
import ArtworkNav from '~/components/artworks/ArtworkNav.vue'
import ArtworkGallery from '~/components/artworks/ArtworkGallery.vue'
import ArtworkLongText from '~/components/artworks/ArtworkLongText.vue'
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
