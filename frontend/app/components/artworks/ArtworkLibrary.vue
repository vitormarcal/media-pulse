<template>
  <section class="art-section">
    <details
      :open="!chronological"
      class="art-library-filters"
      :class="{ 'art-library-filters-visible': !chronological }"
    >
      <summary>Filtrar obras</summary>
      <form class="art-filters" @submit.prevent="load(false)">
        <label>Buscar <input v-model="filters.q" type="search" placeholder="Título, artista, técnica…" /></label>
        <label v-if="!artistId"
          >Artista
          <select v-model="filters.artistId" aria-label="Artista">
            <option value="">Todos</option>
            <option v-for="artist in artists" :key="artist.id" :value="artist.id">{{ artist.name }}</option>
          </select></label
        >
        <label
          >Tipo
          <select v-model="filters.type" aria-label="Tipo">
            <option value="">Todos</option>
            <option v-for="type in options?.types" :key="type">{{ type }}</option>
          </select></label
        >
        <label
          >Movimento
          <select v-model="filters.movement" aria-label="Movimento">
            <option value="">Todos</option>
            <option v-for="movement in options?.movements" :key="movement">{{ movement }}</option>
          </select></label
        >
        <label v-if="!listId"
          >Lista
          <select v-model="filters.listId" aria-label="Lista">
            <option value="">Todas</option>
            <option v-for="list in lists" :key="list.id" :value="list.id">{{ list.name }}</option>
          </select></label
        >
        <label class="art-check"><input v-model="filters.favorite" type="checkbox" /> Favoritos</label>
        <div class="art-actions">
          <button class="art-button" :disabled="busy">Buscar</button
          ><button type="button" class="art-button" :disabled="busy" @click="clear">Limpar</button>
        </div>
      </form>
    </details>
    <p v-if="error" class="art-error" role="alert">
      {{ error }} <button class="art-button" :disabled="busy" @click="load(false)">Tentar novamente</button>
    </p>
    <p v-if="busy" role="status">Carregando obras…</p>
    <p v-else-if="!cards.length && !error" class="art-muted">Nenhuma obra encontrada.</p>
    <div v-if="chronological" class="art-timeline">
      <p v-if="bornAt" class="art-timeline-event">
        <span>{{ yearOf(bornAt) }}</span> Nascimento
      </p>
      <section
        v-for="group in datedGroups"
        :key="group.year"
        class="art-timeline-group"
        :aria-label="`Obras de ${group.year}`"
      >
        <h3 class="art-timeline-year">{{ group.year }}</h3>
        <div class="art-timeline-works">
          <ArtworkCard v-for="item in group.items" :key="item.id" :item="item" hide-artist />
        </div>
      </section>
      <p v-if="diedAt && nextPage === null && !busy && !error" class="art-timeline-event">
        <span>{{ yearOf(diedAt) }}</span> Falecimento
      </p>
      <section v-if="undated.length" class="art-timeline-group art-timeline-undated" aria-label="Obras sem ano">
        <h3 class="art-timeline-year">Sem ano</h3>
        <div class="art-timeline-works">
          <ArtworkCard v-for="item in undated" :key="item.id" :item="item" hide-artist />
        </div>
      </section>
    </div>
    <div v-else class="art-masonry">
      <ArtworkCard v-for="item in cards" :key="item.id" :item="item" />
    </div>
    <button v-if="nextPage !== null" class="art-button" :disabled="busy" @click="load(true)">Carregar mais</button>
  </section>
</template>
<script setup lang="ts">
import ArtworkCard from './ArtworkCard.vue'
import type { ArtworkArtist, ArtworkCard as ArtworkCardData, ArtworkLibrary, ArtworkList } from '~/types/artworks'
const props = defineProps<{
  artistId?: number
  listId?: number
  chronological?: boolean
  bornAt?: string | null
  diedAt?: string | null
}>()
const { request, run, busy, error } = useArtworkApi()
const filters = reactive({
  q: '',
  artistId: '' as number | string,
  type: '',
  movement: '',
  favorite: false,
  listId: '' as number | string,
})
let applied = { ...filters }
const cards = ref<ArtworkCardData[]>([])
const nextPage = ref<number | null>(null)
const datedGroups = computed(() => {
  const groups = new Map<number, ArtworkCardData[]>()
  for (const item of cards.value) {
    if (item.year == null) continue
    const group = groups.get(item.year) || []
    group.push(item)
    groups.set(item.year, group)
  }
  return Array.from(groups, ([year, items]) => ({ year, items }))
})
const undated = computed(() => cards.value.filter((item) => item.year == null))
function yearOf(date: string) {
  return new Date(`${date}T12:00:00Z`).getUTCFullYear()
}
const { data: artists } = await useAsyncData('artwork-library-artists', () => request<ArtworkArtist[]>('/artists'))
const { data: lists } = await useAsyncData('artwork-library-lists', () => request<ArtworkList[]>('/lists'))
const { data: options } = await useAsyncData('artwork-library-filters', () =>
  request<{ types: string[]; movements: string[] }>('/filters'),
)
async function load(more: boolean) {
  await run(async () => {
    const active = more ? applied : { ...filters }
    const result = await request<ArtworkLibrary>('', {
      query: {
        q: active.q || undefined,
        artistId: props.artistId || active.artistId || undefined,
        listId: props.listId || active.listId || undefined,
        type: active.type || undefined,
        movement: active.movement || undefined,
        favorite: active.favorite ? true : undefined,
        sort: props.chronological ? 'year' : undefined,
        page: more ? nextPage.value : 0,
      },
    })
    cards.value = more
      ? [...cards.value, ...result.items.filter((item) => !cards.value.some((old) => old.id === item.id))]
      : result.items
    applied = active
    nextPage.value = result.nextPage
  })
}
async function clear() {
  Object.assign(filters, { q: '', artistId: '', type: '', movement: '', favorite: false, listId: '' })
  await load(false)
}
await load(false)
</script>
