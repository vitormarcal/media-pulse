<template>
  <main class="mag-page">
    <div v-if="pending && !data" class="mag-state" role="status">Carregando revistas…</div>
    <div v-else-if="error" class="mag-state mag-error" role="alert">
      Não foi possível carregar as revistas. <button class="mag-button" @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="data">
      <MoviesCollectionHero
        eyebrow="Revistas"
        title="Revistas"
        intro=""
        :lead="spotlight ? magazineHighlight(spotlight) : null"
        :supporting="[]"
        :show-empty-lead="false"
        accent-link="/magazines?add=1"
        accent-label="Adicionar número"
      />
      <MagazineCollectionNav />
      <div v-show="addMode" ref="issueForm">
        <MagazineIssueForm v-if="openedOnce" :publications="data.publications" @saved="created" @cancel="toggleAdd" />
      </div>
      <template v-if="showEditorial">
        <section v-if="data.overview.inProgress.length" class="mag-section">
          <SectionHeading eyebrow="Leitura" title="Em andamento" />
          <div class="mag-strip">
            <MediaStripCard
              v-for="issue in data.overview.inProgress"
              :key="issue.id"
              :item="magazineShelf(issue)"
              variant="large"
            />
          </div>
        </section>
        <section v-if="data.overview.recent.length" class="mag-section">
          <SectionHeading eyebrow="Histórico" title="Últimas leituras" />
          <div class="mag-strip">
            <MediaStripCard
              v-for="journey in data.overview.recent"
              :key="journey.read.id"
              :item="{
                ...magazineShelf(journey.issue),
                meta: `Lida em ${magazineDate(journey.read.finishedAt)}`,
                detail: journey.issue.totalPages ? `${journey.issue.totalPages} páginas` : '',
              }"
            />
          </div>
        </section>
      </template>
      <MagazineArchive :publications="data.publications" />
    </template>
  </main>
</template>
<script setup lang="ts">
import MoviesCollectionHero from '~/components/movies/MoviesCollectionHero.vue'
import MediaStripCard from '~/components/home/MediaStripCard.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'
import MagazineIssueForm from '~/components/magazines/MagazineIssueForm.vue'
import MagazineArchive from '~/components/magazines/MagazineArchive.vue'
import MagazineCollectionNav from '~/components/magazines/MagazineCollectionNav.vue'
import type { MagazineDetails, MagazineOverview, MagazinePublication } from '~/types/magazines'
import { magazineDate, magazineHighlight, magazineShelf } from '~/utils/magazines'
definePageMeta({
  middleware: (to) => {
    if (to.query.view === 'publications') return navigateTo('/magazines/publications', { replace: true })
    if (to.query.publicationId) {
      const { publicationId, view: _view, ...query } = to.query
      return navigateTo({ path: `/magazines/publications/${publicationId}`, query }, { replace: true })
    }
  },
})
const route = useRoute()
const config = useRuntimeConfig()
const addMode = computed(() => route.query.add === '1')
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
const spotlight = computed(() => data.value?.overview.inProgress[0] || data.value?.overview.recent[0]?.issue)
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
