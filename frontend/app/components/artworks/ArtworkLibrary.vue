<template>
  <section class="art-section">
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
    <p v-if="error" class="art-error" role="alert">
      {{ error }} <button class="art-button" :disabled="busy" @click="load(false)">Tentar novamente</button>
    </p>
    <p v-if="busy" role="status">Carregando obras…</p>
    <p v-else-if="!cards.length && !error" class="art-muted">Nenhuma obra encontrada.</p>
    <div class="art-masonry">
      <NuxtLink v-for="item in cards" :key="item.id" :to="`/artworks/${item.id}`" class="art-work-card">
        <img v-if="item.imageUrl" :src="resolveMediaUrl(item.imageUrl) || undefined" :alt="item.title" loading="lazy" />
        <div v-else class="art-image-fallback">{{ item.type || 'Obra' }}</div>
        <h3>{{ item.title }} <span v-if="item.favorite" aria-label="Favorita">♥</span></h3>
        <p>{{ [item.artist?.name, item.year].filter((part) => part != null).join(' · ') }}</p>
        <p v-if="item.movement" class="art-muted">{{ item.movement }}</p>
      </NuxtLink>
    </div>
    <button v-if="nextPage !== null" class="art-button" :disabled="busy" @click="load(true)">Carregar mais</button>
  </section>
</template>
<script setup lang="ts">
import type { ArtworkArtist, ArtworkCard, ArtworkLibrary, ArtworkList } from '~/types/artworks'
const props = defineProps<{ artistId?: number; listId?: number }>()
const { request, run, busy, error } = useArtworkApi()
const { resolveMediaUrl } = useMediaUrl()
const filters = reactive({
  q: '',
  artistId: '' as number | string,
  type: '',
  movement: '',
  favorite: false,
  listId: '' as number | string,
})
let applied = { ...filters }
const cards = ref<ArtworkCard[]>([])
const nextPage = ref<number | null>(null)
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
