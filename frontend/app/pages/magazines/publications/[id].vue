<template>
  <main class="mag-page">
    <p v-if="pending && !data" class="mag-state" role="status">Carregando publicação…</p>
    <div v-else-if="error" class="mag-state mag-error" role="alert">
      {{ notFound ? 'Publicação não encontrada.' : 'Não foi possível carregar a publicação.' }}
      <NuxtLink v-if="notFound" class="mag-button" to="/magazines/publications">Voltar para publicações</NuxtLink>
      <button v-else class="mag-button" @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="data">
      <MagazineHeader
        :title="data.publication.name"
        :subtitle="`${data.publication.numbersCount} ${data.publication.numbersCount === 1 ? 'número no acervo' : 'números no acervo'}`"
        back-link="/magazines/publications"
        back-label="Voltar para publicações"
        :cover-url="data.latestIssue?.coverUrl"
        :cover-href="data.latestIssue ? `/magazines/${data.latestIssue.id}` : undefined"
        :cover-caption="data.latestIssue ? `Último número publicado · ${magazineNumber(data.latestIssue)}` : undefined"
        ><div v-if="isOwner" class="mag-actions mag-header-actions">
          <NuxtLink class="mag-button primary" :to="`${route.path}${addMode ? '' : '?add=1'}`">{{
            addMode ? 'Fechar cadastro' : 'Adicionar número'
          }}</NuxtLink>
        </div></MagazineHeader
      >
      <MagazineCollectionNav />
      <div v-if="isOwner" v-show="addMode" ref="issueForm">
        <MagazineIssueForm
          v-if="openedOnce"
          :publications="[data.publication]"
          :initial-publication-id="data.publication.id"
          @saved="created"
          @cancel="closeForm"
        />
      </div>
      <section v-if="data.publication.issn || isOwner" class="mag-panel mag-about">
        <h2>Sobre a publicação</h2>
        <p v-if="data.publication.issn">ISSN {{ data.publication.issn }}</p>
        <button
          v-if="isOwner"
          class="mag-button"
          :aria-expanded="editingPublication"
          @click="editingPublication = !editingPublication"
        >
          {{ editingPublication ? 'Fechar edição' : 'Editar publicação' }}</button
        ><MagazinePublicationEditor
          v-if="isOwner"
          v-show="editingPublication"
          :publication="data.publication"
          @saved="publicationSaved"
          @cancel="editingPublication = false"
        />
        <p v-if="feedback" role="status">{{ feedback }}</p>
      </section>
      <MagazineArchive :publication-id="data.publication.id" />
    </template>
  </main>
</template>
<script setup lang="ts">
import MagazineHeader from '~/components/magazines/MagazineHeader.vue'
import MagazinePublicationEditor from '~/components/magazines/MagazinePublicationEditor.vue'
import MagazineArchive from '~/components/magazines/MagazineArchive.vue'
import MagazineCollectionNav from '~/components/magazines/MagazineCollectionNav.vue'
import MagazineIssueForm from '~/components/magazines/MagazineIssueForm.vue'
import type { MagazineDetails, MagazinePublicationDetails } from '~/types/magazines'
import { magazineNumber } from '~/utils/magazines'
definePageMeta({ key: (route) => route.params.id as string })
const owner = useOwnerAuth()
const isOwner = computed(() => owner.value.authenticated)
const editingPublication = ref(false)
const feedback = ref('')
async function publicationSaved() {
  await refresh()
  editingPublication.value = false
  feedback.value = 'Publicação atualizada.'
}
const route = useRoute()
const config = useRuntimeConfig()
const { data, pending, error, refresh } = await useAsyncData(`magazine-publication-${route.params.id}`, () =>
  $fetch<MagazinePublicationDetails>(`/api/magazines/publications/${route.params.id}`, {
    baseURL: config.public.apiBase,
  }),
)
const notFound = computed(() => error.value?.statusCode === 404)
const addMode = computed(() => isOwner.value && route.query.add === '1')
const openedOnce = ref(addMode.value)
const issueForm = ref<HTMLElement>()
watch(addMode, async (open) => {
  if (open) {
    openedOnce.value = true
    await nextTick()
    issueForm.value?.scrollIntoView({
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
      block: 'start',
    })
  }
})
async function closeForm() {
  await navigateTo({ path: route.path, query: { ...route.query, add: undefined } })
}
async function created(details: MagazineDetails) {
  await navigateTo(`/magazines/${details.issue.id}`)
}
</script>
<style scoped src="../../../assets/css/magazines.css"></style>
