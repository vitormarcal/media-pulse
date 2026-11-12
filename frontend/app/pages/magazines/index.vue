<template>
  <main class="mag-page">
    <div v-if="pending && !data" class="mag-state" role="status">Carregando revistas…</div>
    <div v-else-if="error" class="mag-state mag-error" role="alert">
      Não foi possível carregar as revistas. <button class="mag-button" @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="data">
      <MagazineHeader title="Revistas" back-link="/" back-label="Voltar para o início"
        ><div v-if="isOwner" class="mag-actions mag-header-actions">
          <NuxtLink class="mag-button primary" :to="addMode ? '/magazines' : '/magazines?add=1'">{{
            addMode ? 'Fechar cadastro' : 'Adicionar número'
          }}</NuxtLink>
        </div></MagazineHeader
      >
      <MagazineCollectionNav />
      <div v-if="isOwner" v-show="addMode" ref="issueForm">
        <MagazineIssueForm v-if="openedOnce" :publications="data.publications" @saved="created" @cancel="toggleAdd" />
      </div>
      <template v-if="showEditorial"
        ><section v-if="data.overview.inProgress.length" class="mag-panel">
          <MagazineGrid title="Em andamento" :items="data.overview.inProgress.map(magazineCard)" />
        </section>
        <section v-if="data.overview.recent.length" class="mag-panel">
          <MagazineGrid
            title="Últimas leituras"
            :items="
              data.overview.recent.map((journey) => ({
                ...magazineCard(journey.issue),
                id: `read-${journey.read.id}`,
                progressLabel: `Lida em ${magazineDate(journey.read.finishedAt)}`,
                aside: '',
              }))
            "
          /></section
      ></template>
      <MagazineArchive :publications="data.publications" />
    </template>
  </main>
</template>
<script setup lang="ts">
import MagazineHeader from '~/components/magazines/MagazineHeader.vue'
import MagazineGrid from '~/components/magazines/MagazineGrid.vue'
import MagazineIssueForm from '~/components/magazines/MagazineIssueForm.vue'
import MagazineArchive from '~/components/magazines/MagazineArchive.vue'
import MagazineCollectionNav from '~/components/magazines/MagazineCollectionNav.vue'
import type { MagazineDetails, MagazineOverview, MagazinePublication } from '~/types/magazines'
import { magazineDate, magazineCard } from '~/utils/magazines'
definePageMeta({
  middleware: (to) => {
    if (to.query.view === 'publications') return navigateTo('/magazines/publications', { replace: true })
    if (to.query.publicationId) {
      const { publicationId, view: _view, ...query } = to.query
      return navigateTo({ path: `/magazines/publications/${publicationId}`, query }, { replace: true })
    }
  },
})
const owner = useOwnerAuth()
const isOwner = computed(() => owner.value.authenticated)
const route = useRoute()
const config = useRuntimeConfig()
const addMode = computed(() => isOwner.value && route.query.add === '1')
const showEditorial = computed(() => !route.query.q && !route.query.status && !route.query.view && !addMode.value)
const openedOnce = ref(addMode.value)
watch(addMode, (open) => {
  if (open) openedOnce.value = true
})
const { data, pending, error, refresh } = await useAsyncData('magazines-dashboard', async () => {
  const [publications, overview] = await Promise.all([
    $fetch<MagazinePublication[]>('/api/magazines/publications', { baseURL: config.public.apiBase }),
    $fetch<MagazineOverview>('/api/magazines/overview', { baseURL: config.public.apiBase }),
  ])
  return { publications, overview }
})
const issueForm = ref<HTMLElement>()
watch(addMode, async (open) => {
  if (open) {
    await nextTick()
    issueForm.value?.scrollIntoView({
      behavior: window.matchMedia('(prefers-reduced-motion: reduce)').matches ? 'auto' : 'smooth',
      block: 'start',
    })
  }
})
async function toggleAdd() {
  await navigateTo({ path: '/magazines', query: { ...route.query, add: addMode.value ? undefined : '1' } })
}
async function created(details: MagazineDetails) {
  await navigateTo(`/magazines/${details.issue.id}`)
}
</script>
<style scoped src="../../assets/css/magazines.css"></style>
