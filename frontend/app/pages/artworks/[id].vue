<template>
  <main class="art-page art-detail-review">
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
          <MediaImage
            v-if="data.artist.portraitUrl"
            :src="resolveMediaUrl(data.artist.portraitUrl) || undefined"
            alt=""
            :fallback="data.artist.name.slice(0, 1)"
            fallback-class="art-portrait-fallback"
          />
          <span>{{ data.artist.name }}</span>
        </NuxtLink>
        <p v-if="data.artwork.year != null" class="art-muted">{{ data.artwork.year }}</p>
        <div class="art-actions">
          <span v-if="!owner.authenticated && data.artwork.favorite" class="art-muted">♥ Favorita</span>
          <button v-owner class="art-button" :aria-pressed="data.artwork.favorite" :disabled="busy" @click="favorite">
            {{ data.artwork.favorite ? '♥ Favorita' : 'Favoritar' }}
          </button>
        </div>
      </header>
      <p v-if="error && actionContext === 'favorite'" class="art-error" role="alert">{{ error }}</p>
      <ArtworkGallery :images="data.images" :title="data.artwork.title" />
      <section v-if="owner.authenticated || data.rating || data.experiences.length" class="art-personal-memory">
        <MediaRatingPanel
          v-if="owner.authenticated || data.rating"
          media-type="artworks"
          :entity-id="data.artwork.id"
          :initial-rating="data.rating"
          compact
          minimal
          @changed="refresh()"
        />
        <ArtworkExperienceHistory
          v-if="owner.authenticated || data.experiences.length"
          :artwork-id="data.artwork.id"
          :experiences="data.experiences"
          @changed="refresh()"
        />
      </section>
      <section
        v-if="owner.authenticated || hasFacts || data.artwork.description || data.lists.length"
        class="art-about-work art-panel"
      >
        <div class="art-section-header">
          <h2>Sobre a obra</h2>
          <div v-if="owner.authenticated" class="art-actions">
            <button v-owner class="art-button" :aria-expanded="editing" @click="editing = !editing">
              {{ editing ? 'Fechar edição' : 'Editar obra' }}
            </button>
            <button v-if="editing" v-owner class="art-button" :disabled="busy" @click="remove">Excluir obra</button>
          </div>
        </div>
        <p v-if="error && actionContext === 'maintenance'" class="art-error" role="alert">{{ error }}</p>
        <ArtworkForm
          v-if="owner.authenticated && editingOpened"
          v-show="editing"
          :initial="data.artwork"
          @saved="updated"
          @cancel="editing = false"
        />
        <div
          v-if="owner.authenticated && (data.artwork.movementsSyncPending || data.artwork.movementsSyncMessage)"
          class="art-section"
          role="status"
        >
          <p :class="data.artwork.movementsSyncPending ? 'art-error' : 'art-muted'">
            {{
              data.artwork.movementsSyncMessage || 'Movimentos salvos. A atualização no Visual Library está pendente.'
            }}
          </p>
          <button
            v-if="data.artwork.movementsSyncPending"
            v-owner
            class="art-button"
            :disabled="busy"
            @click="retryMovements"
          >
            Tentar sincronizar movimentos novamente
          </button>
        </div>
        <div class="art-reading-layout" :class="{ 'has-description': Boolean(data.artwork.description && hasFacts) }">
          <section v-if="hasFacts" class="art-work-facts" aria-label="Dados da obra">
            <dl
              v-if="data.artwork.type || data.artwork.movements.length || data.artwork.technique"
              class="art-metadata"
            >
              <template v-if="data.artwork.type"
                ><dt>Tipo</dt>
                <dd>{{ data.artwork.type }}</dd></template
              >
              <template v-if="data.artwork.movements.length"
                ><dt>Movimentos e estilos</dt>
                <dd>
                  <ul class="art-movement-chips">
                    <li v-for="movement in data.artwork.movements" :key="movement">{{ movement }}</li>
                  </ul>
                </dd></template
              >
              <template v-if="data.artwork.technique"
                ><dt>Técnica</dt>
                <dd>{{ data.artwork.technique }}</dd></template
              >
            </dl>
          </section>
          <ArtworkLongText :preview-length="420" title="Descrição" :text="data.artwork.description" />
        </div>
        <ArtworkListMembership
          v-if="owner.authenticated || data.lists.length"
          :artwork-id="data.artwork.id"
          :selected="data.lists"
          @changed="refresh()"
        />
        <ArtworkImageManager
          v-if="owner.authenticated"
          :artwork-id="data.artwork.id"
          :images="data.images"
          :title="data.artwork.title"
          management-only
          @changed="refresh()"
        />
      </section>
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
const owner = useOwnerAuth()
const route = useRoute()
const { request, run, busy, error } = useArtworkApi()
const { resolveMediaUrl } = useMediaUrl()
const {
  data,
  pending,
  error: loadError,
  refresh,
} = await useAsyncData(`artwork-${route.params.id}`, () => request<ArtworkDetails>(`/${route.params.id}`))
const hasFacts = computed(
  () => !!(data.value?.artwork.type || data.value?.artwork.movements.length || data.value?.artwork.technique),
)
const editing = ref(false)
const editingOpened = ref(false)
const actionContext = ref<'favorite' | 'maintenance'>('maintenance')
watch(editing, (open) => {
  if (open) editingOpened.value = true
})
async function retryMovements() {
  actionContext.value = 'maintenance'
  await run(async () => {
    data.value = await request<ArtworkDetails>(`/${route.params.id}/movements/sync`, { method: 'POST' })
  })
}
async function updated() {
  editing.value = false
  editingOpened.value = false
  await refresh()
}
async function favorite() {
  actionContext.value = 'favorite'
  await run(async () => {
    data.value = await request<ArtworkDetails>(`/${route.params.id}/favorite`, {
      method: 'PUT',
      body: { favorite: !data.value?.artwork.favorite },
    })
  })
}
async function remove() {
  actionContext.value = 'maintenance'
  if (!window.confirm('Excluir a obra e suas experiências e imagens? Artistas e listas serão preservados.')) return
  await run(async () => {
    await request(`/${route.params.id}`, { method: 'DELETE' })
    await navigateTo('/artworks')
  })
}
</script>
<style src="../../assets/css/artworks.css"></style>
