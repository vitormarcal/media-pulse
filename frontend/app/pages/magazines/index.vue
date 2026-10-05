<template>
  <main class="mag-page">
    <div v-if="pending && !data" class="mag-state" role="status">Carregando revistas…</div>
    <div v-else-if="error" class="mag-state mag-error" role="alert">
      Não foi possível carregar as revistas. <button class="mag-button" @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="data">
      <MoviesCollectionHero
        v-if="showEditorial"
        eyebrow="Revistas"
        title="Revistas"
        intro=""
        :lead="spotlight ? magazineHighlight(spotlight) : null"
        :supporting="[]"
        :show-empty-lead="false"
        accent-link="/magazines?add=1"
        accent-label="Adicionar revista"
      />
      <BooksLibraryHero
        v-else
        eyebrow="Revistas"
        :title="filtered ? 'Resultados' : 'Revistas'"
        intro=""
        back-link="/magazines"
        back-label="Voltar"
        accent-link="/magazines?add=1"
        accent-label="Adicionar revista"
        :spotlight="
          spotlight
            ? {
                title: spotlight.publication.name,
                subtitle: magazineNumber(spotlight),
                imageUrl: spotlight.coverUrl,
                href: `/magazines/${spotlight.id}`,
                meta: magazineCard(spotlight).progressLabel,
                note: '',
              }
            : null
        "
        :show-empty-spotlight="false"
      />
      <div v-show="addMode" ref="issueForm">
        <MagazineIssueForm
          v-if="openedOnce"
          v-show="addMode"
          :publications="data.publications"
          @saved="created"
          @cancel="toggleAdd"
        />
      </div>
      <template v-if="showEditorial && data.overview">
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
          <div class="mag-grid">
            <MediaPosterCard
              v-for="issue in data.overview.recent"
              :key="issue.id"
              :item="magazineShelf(issue)"
              variant="standard"
            />
          </div>
        </section>
      </template>
      <section class="mag-section">
        <form class="mag-panel mag-filters" @submit.prevent="applyFilters">
          <label
            ><span>Buscar revista ou número</span
            ><input v-model="draftQuery" type="search" placeholder="Nome, número, mês/ano ou ISSN"
          /></label>
          <label
            ><span>Publicação</span
            ><select v-model="draftPublication">
              <option value="">Todas</option>
              <option
                v-for="publicationOption in data.publications"
                :key="publicationOption.id"
                :value="String(publicationOption.id)"
              >
                {{ publicationOption.name }}
              </option>
            </select></label
          >
          <label
            ><span>Estado</span
            ><select v-model="draftStatus">
              <option value="">Todos</option>
              <option v-for="(label, value) in magazineStates" :key="value" :value="value">{{ label }}</option>
            </select></label
          >
          <div class="mag-actions">
            <button class="mag-button primary" type="submit">Filtrar</button
            ><NuxtLink v-if="filtered" class="mag-button" to="/magazines?view=archive">Limpar</NuxtLink>
          </div>
        </form>
        <BooksLibraryGrid
          eyebrow="Arquivo"
          :title="filtered ? 'Resultados' : 'Todos os números'"
          description=""
          summary=""
          :items="items.map(magazineCard)"
          empty-message="Nenhuma revista encontrada."
        />
        <div v-if="nextPage != null" class="mag-actions">
          <button class="mag-button" :disabled="loadingMore" @click="loadMore">
            {{ loadingMore ? 'Buscando mais revistas…' : 'Carregar mais revistas' }}
          </button>
        </div>
        <p v-if="moreError" class="mag-error" role="alert">{{ moreError }}</p>
      </section>
    </template>
  </main>
</template>
<script setup lang="ts">
import BooksLibraryGrid from '~/components/books/BooksLibraryGrid.vue'
import BooksLibraryHero from '~/components/books/BooksLibraryHero.vue'
import MoviesCollectionHero from '~/components/movies/MoviesCollectionHero.vue'
import MediaStripCard from '~/components/home/MediaStripCard.vue'
import MediaPosterCard from '~/components/home/MediaPosterCard.vue'
import SectionHeading from '~/components/home/SectionHeading.vue'
import MagazineIssueForm from '~/components/magazines/MagazineIssueForm.vue'
import type {
  MagazineDetails,
  MagazineIssue,
  MagazineLibrary,
  MagazineOverview,
  MagazinePublication,
} from '~/types/magazines'
import {
  magazineCard,
  magazineError,
  magazineNumber,
  magazineStates,
  magazineHighlight,
  magazineShelf,
} from '~/utils/magazines'
const route = useRoute()
const config = useRuntimeConfig()
const query = computed(() => String(route.query.q || ''))
const publication = computed(() => String(route.query.publicationId || ''))
const selectedStatus = computed(() => String(route.query.status || ''))
const filtered = computed(() => Boolean(query.value || publication.value || selectedStatus.value))
const showEditorial = computed(() => !filtered.value && route.query.view !== 'archive' && !addMode.value)
const addMode = computed(() => route.query.add === '1')
const openedOnce = ref(addMode.value)
watch(addMode, (open) => {
  if (open) openedOnce.value = true
})
const draftQuery = ref(query.value)
const draftPublication = ref(publication.value)
const draftStatus = ref(selectedStatus.value)
const filters = computed(() => ({
  q: query.value || undefined,
  publicationId: publication.value || undefined,
  status: selectedStatus.value || undefined,
}))
const { data, pending, error, refresh } = await useAsyncData(
  'magazines-library',
  async () => {
    const [library, publications, overview] = await Promise.all([
      $fetch<MagazineLibrary>('/api/magazines', { baseURL: config.public.apiBase, query: filters.value }),
      $fetch<MagazinePublication[]>('/api/magazines/publications', { baseURL: config.public.apiBase }),
      $fetch<MagazineOverview>('/api/magazines/overview', { baseURL: config.public.apiBase }),
    ])
    return { library, publications, overview }
  },
  { watch: [query, publication, selectedStatus] },
)
const extra = ref<MagazineIssue[]>([])
const nextPage = ref<number | null>(null)
const moreError = ref('')
const loadingMore = ref(false)
watch(
  data,
  (value) => {
    extra.value = []
    nextPage.value = value?.library.nextPage ?? null
    moreError.value = ''
  },
  { immediate: true },
)
watch([query, publication, selectedStatus], () => {
  draftQuery.value = query.value
  draftPublication.value = publication.value
  draftStatus.value = selectedStatus.value
  extra.value = []
  nextPage.value = null
})
const items = computed(() => [...(data.value?.library.items || []), ...extra.value])
const spotlight = computed(
  () => data.value?.overview.inProgress[0] || data.value?.overview.recent[0] || data.value?.library.items[0],
)
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
async function applyFilters() {
  await navigateTo({
    path: '/magazines',
    query: {
      q: draftQuery.value || undefined,
      publicationId: draftPublication.value || undefined,
      status: draftStatus.value || undefined,
      view: 'archive',
    },
  })
}
async function created(details: MagazineDetails) {
  await navigateTo(`/magazines/${details.issue.id}`)
}
async function loadMore() {
  if (nextPage.value == null || loadingMore.value) return
  const filterKey = JSON.stringify(filters.value)
  loadingMore.value = true
  moreError.value = ''
  try {
    const result = await $fetch<MagazineLibrary>('/api/magazines', {
      baseURL: config.public.apiBase,
      query: { ...filters.value, page: nextPage.value },
    })
    if (filterKey !== JSON.stringify(filters.value)) return
    const known = new Set(items.value.map((item) => item.id))
    extra.value.push(...result.items.filter((item) => !known.has(item.id)))
    nextPage.value = result.nextPage
  } catch (cause) {
    moreError.value = magazineError(cause)
  } finally {
    loadingMore.value = false
  }
}
</script>
<style scoped src="../../assets/css/magazines.css"></style>
