<template>
  <main class="mag-page">
    <p v-if="pending && !data" class="mag-state" role="status">Carregando publicação…</p>
    <div v-else-if="error" class="mag-state mag-error" role="alert">
      {{ notFound ? 'Publicação não encontrada.' : 'Não foi possível carregar a publicação.' }}
      <NuxtLink v-if="notFound" class="mag-button" to="/magazines/publications">Voltar para publicações</NuxtLink>
      <button v-else class="mag-button" @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="data">
      <BooksLibraryHero
        eyebrow="Revistas"
        :title="data.publication.name"
        :intro="`${data.publication.numbersCount} ${data.publication.numbersCount === 1 ? 'número' : 'números'}`"
        back-link="/magazines/publications"
        back-label="Voltar para publicações"
        :accent-link="`${route.path}?add=1`"
        accent-label="Adicionar número"
        spotlight-label="Último número"
        :spotlight="
          data.latestIssue
            ? {
                title: data.publication.name,
                subtitle: magazineNumber(data.latestIssue),
                imageUrl: data.latestIssue.coverUrl,
                href: `/magazines/${data.latestIssue.id}`,
                meta: '',
                note: '',
              }
            : null
        "
        :show-empty-spotlight="false"
      />
      <MagazineCollectionNav />
      <div v-show="addMode" ref="issueForm">
        <MagazineIssueForm
          v-if="openedOnce"
          :publications="[data.publication]"
          :initial-publication-id="data.publication.id"
          @saved="created"
          @cancel="closeForm"
        />
      </div>
      <details v-if="data.publication.issn" class="mag-publication-details">
        <summary>Detalhes da publicação</summary>
        <p>ISSN {{ data.publication.issn }}</p>
      </details>
      <MagazineArchive :publication-id="data.publication.id" />
    </template>
  </main>
</template>
<script setup lang="ts">
import BooksLibraryHero from '~/components/books/BooksLibraryHero.vue'
import MagazineArchive from '~/components/magazines/MagazineArchive.vue'
import MagazineCollectionNav from '~/components/magazines/MagazineCollectionNav.vue'
import MagazineIssueForm from '~/components/magazines/MagazineIssueForm.vue'
import type { MagazineDetails, MagazinePublicationDetails } from '~/types/magazines'
import { magazineNumber } from '~/utils/magazines'
definePageMeta({ key: (route) => route.params.id as string })
const route = useRoute()
const config = useRuntimeConfig()
const { data, pending, error, refresh } = await useAsyncData(`magazine-publication-${route.params.id}`, () =>
  $fetch<MagazinePublicationDetails>(`/api/magazines/publications/${route.params.id}`, {
    baseURL: config.public.apiBase,
  }),
)
const notFound = computed(() => error.value?.statusCode === 404)
const addMode = computed(() => route.query.add === '1')
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
