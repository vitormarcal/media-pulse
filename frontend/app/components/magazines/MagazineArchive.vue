<template>
  <section class="mag-section">
    <form
      class="mag-panel mag-filters"
      :class="{ 'publication-filters': publicationId }"
      @submit.prevent="applyFilters"
    >
      <label
        ><span>{{ publicationId ? 'Buscar neste arquivo' : 'Buscar revista ou número' }}</span>
        <input
          v-model="draftQuery"
          type="search"
          :placeholder="publicationId ? 'Número ou mês/ano' : 'Nome, número, mês/ano ou ISSN'"
        />
      </label>
      <label v-if="!publicationId"
        ><span>Publicação</span>
        <select v-model="draftPublication">
          <option value="">Todas</option>
          <option v-for="entry in publications" :key="entry.id" :value="String(entry.id)">{{ entry.name }}</option>
        </select>
      </label>
      <label
        ><span>Leitura</span>
        <select v-model="draftStatus">
          <option value="">Todas</option>
          <option v-for="(label, value) in magazineStates" :key="value" :value="value">{{ label }}</option>
          <option value="UNREAD">Não lida</option>
        </select>
      </label>
      <label v-if="!publicationId"
        ><span>Ordenar por</span>
        <select value="PUBLICATION_DATE">
          <option value="PUBLICATION_DATE">Publicação mais recente</option>
        </select>
      </label>
      <div class="mag-actions">
        <button class="mag-button primary" type="submit">Filtrar</button>
        <NuxtLink v-if="filtered" class="mag-button" :to="clearLink">Limpar</NuxtLink>
      </div>
    </form>
    <p v-if="pending && !data" class="mag-state" role="status">Carregando números…</p>
    <div v-else-if="error" class="mag-state mag-error" role="alert">
      Não foi possível carregar o acervo. <button class="mag-button" @click="refresh()">Tentar novamente</button>
    </div>
    <template v-else-if="data">
      <template v-if="publicationId">
        <BooksLibraryGrid
          v-for="group in yearGroups"
          :key="group.year"
          layout="regular"
          eyebrow=""
          :title="group.year"
          description=""
          summary=""
          :items="group.items.map(magazineCard)"
        />
        <p v-if="!items.length" class="mag-muted">Nenhum número encontrado.</p>
      </template>
      <BooksLibraryGrid
        v-else
        layout="regular"
        eyebrow="Arquivo"
        title="Todos os números"
        description=""
        summary=""
        :items="items.map(magazineCard)"
        empty-message="Nenhum número encontrado."
      />
      <div v-if="nextPage != null" class="mag-actions">
        <button class="mag-button" :disabled="loadingMore" @click="loadMore">
          {{ loadingMore ? 'Carregando…' : 'Carregar mais números' }}
        </button>
      </div>
      <p v-if="moreError" class="mag-error" role="alert">{{ moreError }}</p>
    </template>
  </section>
</template>
<script setup lang="ts">
import BooksLibraryGrid from '~/components/books/BooksLibraryGrid.vue'
import type { MagazineIssue, MagazineLibrary, MagazinePublication } from '~/types/magazines'
import { magazineCard, magazineError, magazineStates } from '~/utils/magazines'
const props = defineProps<{ publicationId?: number; publications?: MagazinePublication[] }>()
const route = useRoute()
const config = useRuntimeConfig()
const query = computed(() => String(route.query.q || ''))
const selectedPublication = computed(() =>
  props.publicationId ? String(props.publicationId) : String(route.query.publicationId || ''),
)
const status = computed(() => String(route.query.status || ''))
const draftQuery = ref(query.value)
const draftPublication = ref(selectedPublication.value)
const draftStatus = ref(status.value)
const filters = computed(() => ({
  q: query.value || undefined,
  publicationId: selectedPublication.value || undefined,
  status: status.value === 'UNREAD' ? undefined : status.value || undefined,
  unread: status.value === 'UNREAD' ? true : undefined,
  order: 'PUBLICATION_DATE',
}))
const filtered = computed(() =>
  Boolean(query.value || status.value || (!props.publicationId && selectedPublication.value)),
)
const clearLink = computed(() =>
  props.publicationId ? `/magazines/publications/${props.publicationId}` : '/magazines?view=archive',
)
const { data, pending, error, refresh } = await useAsyncData(
  `magazines-archive-${props.publicationId || 'all'}`,
  () => $fetch<MagazineLibrary>('/api/magazines', { baseURL: config.public.apiBase, query: filters.value }),
  { watch: [filters] },
)
const extra = ref<MagazineIssue[]>([])
const nextPage = ref<number | null>(null)
const loadingMore = ref(false)
const moreError = ref('')
watch(
  data,
  (value) => {
    extra.value = []
    nextPage.value = value?.nextPage ?? null
    moreError.value = ''
  },
  { immediate: true },
)
watch(filters, () => {
  draftQuery.value = query.value
  draftPublication.value = selectedPublication.value
  draftStatus.value = status.value
  extra.value = []
  nextPage.value = null
})
const items = computed(() => [...(data.value?.items || []), ...extra.value])
const yearGroups = computed(() => {
  const groups = new Map<string, MagazineIssue[]>()
  for (const issue of items.value) {
    const year = issue.coverDate?.slice(0, 4) || 'Sem data'
    const group = groups.get(year) || []
    group.push(issue)
    groups.set(year, group)
  }
  return [...groups].map(([year, issues]) => ({ year, items: issues }))
})
async function applyFilters() {
  await navigateTo({
    path:
      props.publicationId || draftPublication.value
        ? `/magazines/publications/${props.publicationId || draftPublication.value}`
        : '/magazines',
    query: {
      q: draftQuery.value || undefined,

      status: draftStatus.value || undefined,
      view: props.publicationId || draftPublication.value ? undefined : 'archive',
    },
  })
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
